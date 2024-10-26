plugins {
    id("java")
    id("idea")
    id("com.google.protobuf") version "0.9.4"
}

group = "com.grpcperf"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    // Add Apache repository
    maven { url = uri("https://repository.apache.org/content/repositories/releases/") }
}

dependencies {
    // JMeter DSL with BOM exclusion
    testImplementation("us.abstracta.jmeter:jmeter-java-dsl:1.29.1") {
        exclude("org.apache.jmeter", "bom")
    }

    // gRPC dependencies
    implementation("io.grpc:grpc-netty-shaded:1.58.0")
    implementation("io.grpc:grpc-protobuf:1.58.0")
    implementation("io.grpc:grpc-stub:1.58.0")

    // for proto compilation
    implementation("com.google.protobuf:protobuf-java:3.24.0")

    // testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.24.0"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.58.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("load", "performance", "reliability")  // exclude performance-related tests
    }
}

// custom tasks for performance testing
tasks.register<Test>("runLoadTest") {
    description = "Runs load tests"
    group = "verification"
    useJUnitPlatform {
        includeTags("load", "performance")
    }
    maxHeapSize = "1g"
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("runReliabilityTest") {
    description = "Runs reliability tests"
    group = "verification"
    useJUnitPlatform {
        includeTags("reliability")
    }
    maxHeapSize = "1g"
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}