plugins {
    id("java")
    id("idea")
    id("com.google.protobuf") version "0.9.4"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repository.apache.org/content/repositories/releases/") }
    // JMeter plugins repo
    maven { url = uri("https://jmeter-plugins.org/repo/") }
}


dependencies {
    // JMeter DSL with BOM exclusion
    implementation("us.abstracta.jmeter:jmeter-java-dsl:1.29.1") {
        exclude("org.apache.jmeter", "bom")
    }
    implementation("org.apache.jmeter:ApacheJMeter_core:5.6.3")
    implementation("org.apache.jmeter:ApacheJMeter_java:5.6.3")

    // gRPC dependencies
    implementation(platform("io.grpc:grpc-bom:1.58.0"))

    implementation("io.grpc:grpc-netty-shaded")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")

    implementation("com.google.protobuf:protobuf-java:4.28.3")
    implementation("com.google.protobuf:protobuf-java-util:4.28.3")


    // testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    // logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // InfluxDB2 listener
    implementation("io.github.mderevyankoaqa:jmeter-plugins-influxdb2-listener:2.8")
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
        artifact = "com.google.protobuf:protoc:4.28.3"
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
    sourceSets {
        main {
            proto {
                // outside of the java project
                srcDir("../../../grpc-perf-lab/helloworld")
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
        includeTags("load")
    }
    maxHeapSize = "2g"
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
    maxHeapSize = "2g"
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
