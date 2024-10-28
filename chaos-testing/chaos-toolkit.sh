#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

###################
# Constants
###################
readonly SCRIPT_NAME=$(basename "${0}")
readonly SCRIPT_DIR=$(cd "$(dirname "${0}")" && pwd)
readonly DEFAULT_CONFIG_FILE="${SCRIPT_DIR}/chaos-config.env"
readonly LOG_DIR="/var/log/chaos-toolkit"
readonly LOG_FILE="${LOG_DIR}/chaos-toolkit.log"
readonly METRICS_FILE="${LOG_DIR}/metrics.json"
readonly STATE_FILE="${LOG_DIR}/chaos-state.json"
readonly LOCK_FILE="/var/run/chaos-toolkit.lock"

###################
# Default config
###################
declare -A CONFIG=(
    [CONTAINER_NAME]="grpc-service"
    [SEED]="42"
    [PROB_NETWORK_ISSUES]="0.1"
    [PROB_RESOURCE_LIMIT]="0.05"
    [PROB_CONTAINER_ACTIONS]="0.02"
    [NETWORK_LATENCY_MAX]="200"
    [PACKET_LOSS_MAX]="15"
    [CPU_LIMIT_MIN]="10"
    [MEMORY_LIMIT_MIN]="512"
    [DURATION_MIN]="30"
    [DURATION_MAX]="180"
    [DRY_RUN]="false"
    [DEBUG]="false"
)

debug() {
    if [[ "${CONFIG[DEBUG]}" == "true" ]]; then
        echo "[DEBUG] [$(date '+%Y-%m-%d %H:%M:%S')] $*" >&2
    fi
}

info() {
    echo "[INFO] [$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "${LOG_FILE}"
}

error() {
    echo "[ERROR] [$(date '+%Y-%m-%d %H:%M:%S')] $*" >&2 | tee -a "${LOG_FILE}"
}

die() {
    error "$*"
    exit 1
}

acquire_lock() {
    exec 9>"${LOCK_FILE}"
    if ! flock -n 9; then
        die "Another instance is running"
    fi
}

check_dependencies() {
    local -r deps=(docker tc iptables jq)
    local missing=()

    for dep in "${deps[@]}"; do
        if ! command -v "$dep" >/dev/null 2>&1; then
            missing+=("$dep")
        fi
    done

    if ((${#missing[@]} > 0)); then
        die "Missing dependencies: ${missing[*]}"
    fi
}

check_permissions() {
    if [[ $EUID -ne 0 ]]; then
        die "This script must be run as root"
    fi
}

validate_config() {
    local error_count=0

    for prob in PROB_NETWORK_ISSUES PROB_RESOURCE_LIMIT PROB_CONTAINER_ACTIONS; do
        if ! awk -v val="${CONFIG[$prob]}" 'BEGIN{exit !(val >= 0 && val <= 1)}'; then
            error "Invalid probability for ${prob}: ${CONFIG[$prob]}"
            ((error_count++))
        fi
    done

    for num in NETWORK_LATENCY_MAX PACKET_LOSS_MAX CPU_LIMIT_MIN MEMORY_LIMIT_MIN DURATION_MIN DURATION_MAX; do
        if ! [[ "${CONFIG[$num]}" =~ ^[0-9]+$ ]]; then
            error "Invalid numeric value for ${num}: ${CONFIG[$num]}"
            ((error_count++))
        fi
    done

    if ! docker ps -q -f name="^/${CONFIG[CONTAINER_NAME]}$" >/dev/null; then
        error "Container ${CONFIG[CONTAINER_NAME]} not found"
        ((error_count++))
    fi

    # check if min duration is less than max duration
    if ((CONFIG[DURATION_MIN] >= CONFIG[DURATION_MAX])); then
        error "DURATION_MIN must be less than DURATION_MAX"
        ((error_count++))
    fi

    if ((error_count > 0)); then
        die "Configuration validation failed with ${error_count} errors"
    fi
}

load_config() {
    local config_file="${1:-${DEFAULT_CONFIG_FILE}}"

    if [[ -f "$config_file" ]]; then
        debug "Loading config from $config_file"
        # shellcheck source=/dev/null
        source "$config_file"

        for key in "${!CONFIG[@]}"; do
            if [[ -n "${!key:-}" ]]; then
                CONFIG[$key]="${!key}"
            fi
        done
    else
        debug "Config file not found, using defaults"
    fi

    validate_config
}

###################
# Metrics functions
###################
update_metrics() {
    local action=$1
    local details=$2
    local timestamp
    timestamp=$(date '+%s')

    local metrics
    if [[ -f "${METRICS_FILE}" ]]; then
        metrics=$(cat "${METRICS_FILE}")
    else
        metrics='{}'
    fi

    metrics=$(jq --arg action "$action" \
                 --arg details "$details" \
                 --arg timestamp "$timestamp" \
                 '.actions += [{"action": $action, "details": $details, "timestamp": $timestamp}]' \
                 <<<"$metrics")

    echo "$metrics" > "${METRICS_FILE}"
}

###################
# Chaos functions
###################
inject_network_latency() {
    local latency duration
    latency=$((RANDOM % CONFIG[NETWORK_LATENCY_MAX]))
    duration=$((RANDOM % (CONFIG[DURATION_MAX] - CONFIG[DURATION_MIN]) + CONFIG[DURATION_MIN]))

    info "Injecting network latency: ${latency}ms for ${duration}s"

    if [[ "${CONFIG[DRY_RUN]}" != "true" ]]; then
        tc qdisc add dev eth0 root netem delay "${latency}ms" || true
        update_metrics "network_latency" "latency=${latency}ms duration=${duration}s"
        sleep "$duration"
        tc qdisc del dev eth0 root || true
    fi
}

inject_packet_loss() {
    local loss duration
    loss=$((RANDOM % CONFIG[PACKET_LOSS_MAX]))
    duration=$((RANDOM % (CONFIG[DURATION_MAX] - CONFIG[DURATION_MIN]) + CONFIG[DURATION_MIN]))

    info "Injecting packet loss: ${loss}% for ${duration}s"

    if [[ "${CONFIG[DRY_RUN]}" != "true" ]]; then
        tc qdisc add dev eth0 root netem loss "${loss}%" || true
        update_metrics "packet_loss" "loss=${loss}% duration=${duration}s"
        sleep "$duration"
        tc qdisc del dev eth0 root || true
    fi
}

limit_cpu() {
    local cpu_limit duration
    cpu_limit=$((RANDOM % (100 - CONFIG[CPU_LIMIT_MIN]) + CONFIG[CPU_LIMIT_MIN]))
    duration=$((RANDOM % (CONFIG[DURATION_MAX] - CONFIG[DURATION_MIN]) + CONFIG[DURATION_MIN]))

    info "Limiting CPU to ${cpu_limit}% for ${duration}s"

    if [[ "${CONFIG[DRY_RUN]}" != "true" ]]; then
        docker update --cpus="0.${cpu_limit}" "${CONFIG[CONTAINER_NAME]}" || true
        update_metrics "cpu_limit" "limit=${cpu_limit}% duration=${duration}s"
        sleep "$duration"
        docker update --cpus="2" "${CONFIG[CONTAINER_NAME]}" || true
    fi
}

limit_memory() {
    local memory_limit duration
    memory_limit=$((RANDOM % (2048 - CONFIG[MEMORY_LIMIT_MIN]) + CONFIG[MEMORY_LIMIT_MIN]))
    duration=$((RANDOM % (CONFIG[DURATION_MAX] - CONFIG[DURATION_MIN]) + CONFIG[DURATION_MIN]))

    info "Limiting memory to ${memory_limit}M for ${duration}s"

    if [[ "${CONFIG[DRY_RUN]}" != "true" ]]; then
        docker update --memory="${memory_limit}m" "${CONFIG[CONTAINER_NAME]}" || true
        update_metrics "memory_limit" "limit=${memory_limit}m duration=${duration}s"
        sleep "$duration"
        docker update --memory="2g" "${CONFIG[CONTAINER_NAME]}" || true
    fi
}

container_actions() {
    local action duration
    action=$((RANDOM % 2))
    duration=$((RANDOM % (CONFIG[DURATION_MAX] - CONFIG[DURATION_MIN]) + CONFIG[DURATION_MIN]))

    case $action in
        0)
            info "Pausing container for ${duration}s"
            if [[ "${CONFIG[DRY_RUN]}" != "true" ]]; then
                docker pause "${CONFIG[CONTAINER_NAME]}" || true
                update_metrics "container_pause" "duration=${duration}s"
                sleep "$duration"
                docker unpause "${CONFIG[CONTAINER_NAME]}" || true
            fi
            ;;
        1)
            info "Restarting container"
            if [[ "${CONFIG[DRY_RUN]}" != "true" ]]; then
                docker restart "${CONFIG[CONTAINER_NAME]}" || true
                update_metrics "container_restart" "timestamp=$(date +%s)"
            fi
            ;;
    esac
}

cleanup() {
    info "Cleaning up chaos..."

    if [[ "${CONFIG[DRY_RUN]}" != "true" ]]; then
        tc qdisc del dev eth0 root 2>/dev/null || true
        docker update --cpus="2" --memory="2g" "${CONFIG[CONTAINER_NAME]}" 2>/dev/null || true
    fi

    update_metrics "cleanup" "timestamp=$(date +%s)"
    info "Cleanup completed"

    flock -u 9
}

should_execute() {
    local prob=$1
    local rand
    rand=$(awk -v min=0 -v max=100 'BEGIN{srand(); print int(min+rand()*(max-min+1))}')
    local threshold
    threshold=$(awk "BEGIN {print $prob * 100}")

    ((rand < threshold))
}

print_status() {
    local uptime
    uptime=$(awk '{print $1}' /proc/uptime)

    cat << EOF
Chaos toolkit Status:
-------------------
Uptime: ${uptime}s
Container: ${CONFIG[CONTAINER_NAME]}
Seed: ${CONFIG[SEED]}
Network Issues Probability: ${CONFIG[PROB_NETWORK_ISSUES]}
Resource Limit Probability: ${CONFIG[PROB_RESOURCE_LIMIT]}
Container Actions Probability: ${CONFIG[PROB_CONTAINER_ACTIONS]}
Dry Run: ${CONFIG[DRY_RUN]}
Debug: ${CONFIG[DEBUG]}
EOF

    if [[ -f "${METRICS_FILE}" ]]; then
        echo -e "\nLast 5 actions:"
        jq -r '.actions[-5:] | .[] | "  \(.timestamp | strftime("%Y-%m-%d %H:%M:%S")) \(.action): \(.details)"' "${METRICS_FILE}"
    fi
}

###################
# Main
###################
main() {
    mkdir -p "${LOG_DIR}"
    acquire_lock
    check_dependencies
    check_permissions
    load_config "$@"

    RANDOM=${CONFIG[SEED]}

    trap cleanup EXIT INT TERM

    info "Starting chaos toolkit with seed: ${CONFIG[SEED]}"

    # Main loop
    while true; do
        if should_execute "${CONFIG[PROB_NETWORK_ISSUES]}"; then
            if ((RANDOM % 2)); then
                inject_network_latency
            else
                inject_packet_loss
            fi
        fi

        if should_execute "${CONFIG[PROB_RESOURCE_LIMIT]}"; then
            if ((RANDOM % 2)); then
                limit_cpu
            else
                limit_memory
            fi
        fi

        if should_execute "${CONFIG[PROB_CONTAINER_ACTIONS]}"; then
            container_actions
        fi

        sleep 60
    done
}

###################
# Script execution
###################
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi