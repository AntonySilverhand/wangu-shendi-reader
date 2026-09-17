plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.jsoup)
    testImplementation(libs.junit)
}

tasks.register<JavaExec>("dumpContracts") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.wanshu.reader.core.source.SourceContractDumper")
    args = listOf(rootProject.file("../contracts/native/source-contracts-native.json").absolutePath)
}

