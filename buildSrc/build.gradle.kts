plugins {
    `kotlin-dsl`
    id("com.github.ben-manes.versions") version "0.39.0"
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.android.tools.build:gradle:7.2.2")
    implementation(kotlin("gradle-plugin", "1.7.10"))
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.43.2")
}

tasks {
    //检查依赖库更新
    //gradlew -p buildSrc dependencyUpdates
    dependencyUpdates {
        rejectVersionIf {
            isNonStable(candidate.version)
        }
        checkForGradleUpdate = true
        outputFormatter = "html"
        outputDir = "build/dependencyUpdates"
        reportfileName = "report"
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}