/*
 * Copyright (c) 2019 Toast Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.protobuf.gradle.proto
import com.toasttab.protokt.gradle.CODEGEN_NAME
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.20")
    }
}

plugins {
    id("protokt.jvm-conventions")
    id("com.google.protobuf")
    application
}

defaultProtoc()

// Enable Kotlin 1.7 in codegen only; even though the buildscript dependency
// has been forced to 1.7.x, Gradle gets confused and thinks we're running in
// the context of Kotlin 1.5.
tasks.withType<KotlinCompile> {
    kotlinOptions {
        // Prevent checks of class metadata version:
        // `Class 'kotlin.Unit' was compiled with an incompatible version of Kotlin. The binary version of its metadata is 1.7.1, expected version is 1.5.1.`
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
}

enablePublishing(defaultJars = false)

application {
    applicationName = CODEGEN_NAME
    mainClass.set("com.toasttab.protokt.MainKt")
}

dependencies {
    implementation(project(":extensions:protokt-extensions-api"))
    implementation(project(":protokt-runtime"))
    implementation(project(":protokt-runtime-grpc"))
    implementation(project(":protokt-util"))

    implementation(kotlin("reflect", "1.7.20"))

    implementation(libs.arrow)
    implementation(libs.grpcStub)
    implementation(libs.kotlinPoet)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.ktlint)
    implementation(libs.ktlintStandardRuleSet)
    implementation(libs.protobufJava)

    testImplementation(project(":testing:testing-util"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("dist") {
            artifact(
                mapOf(
                    "source" to tasks.findByName("distZip"),
                    "extension" to "zip",
                    "classifier" to "dist"
                )
            )
            artifactId = project.name
            version = "${rootProject.version}"
            groupId = "${rootProject.group}"
        }
    }
}

tasks.withType<Test> {
    afterEvaluate {
        environment("PROTOC_PATH", configurations.named("protobufToolsLocator_protoc").get().singleFile)
    }
}

sourceSets {
    main {
        proto {
            srcDir("../extensions/protokt-extensions-lite/src/main/proto")
        }
    }
}
