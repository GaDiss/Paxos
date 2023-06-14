plugins {
    id("java")
    id("idea")
    id("application")
}

group = "itmo.spankratov.bfts23"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.typesafe.akka:akka-actor-typed_2.13:2.8.2")
    implementation("ch.qos.logback:logback-classic:1.2.9")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.typesafe.akka:akka-actor-testkit-typed_2.13:2.8.2")
}

tasks.test {
    useJUnitPlatform()
}