/*
 * Copyright (c) 2026 Toast, Inc.
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

import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import protokt.v1.gradle.Os

plugins {
    `kotlin-multiplatform`
    id("protokt.common-conventions")
    `java-base`
}

the<SourceSetContainer>().create("main")
the<SourceSetContainer>().create("test")

kotlin {
    jvm {
        compilerOptions {
            jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
        }
    }

    when (Os.current.kind) {
        Os.Kind.MACOS -> when (Os.current.arch) {
            Os.Arch.ARM64 -> macosArm64()
            Os.Arch.X64 -> macosX64()
            else -> Unit
        }
        Os.Kind.LINUX -> when (Os.current.arch) {
            Os.Arch.X64 -> linuxX64()
            Os.Arch.ARM64 -> linuxArm64()
            else -> Unit
        }
        else -> Unit
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.junit.jupiter)
                implementation(libs.truth)
            }
        }
    }

    compilerOptions {
        configureKotlin()
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

configureJvmToolchain()
