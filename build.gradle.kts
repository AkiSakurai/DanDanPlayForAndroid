buildscript {
    repositories {
        google()
        mavenCentral()
        jcenter()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        jcenter()
        maven("https://developer.huawei.com/repo/")
    }
}

tasks {
    val clean by registering(Delete::class) {
        delete(buildDir)
    }
}