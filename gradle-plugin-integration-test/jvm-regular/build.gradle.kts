/*
 * Copyright (c) 2021 Toast Inc.
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

import com.toasttab.protokt.gradle.protoktExtensions

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.toasttab.protokt")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
}

dependencies {
    protoktExtensions("com.toasttab.protokt:protokt-jvm-extensions:$version")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
    testImplementation("com.google.protobuf:protobuf-java:3.19.1")
    testImplementation("com.toasttab.protokt:protokt-util:$version")
}

sourceSets {
    main {
        proto {
            srcDir("../multiplatform/src/main/proto")
        }
    }
    test {
        java {
            srcDir("../multiplatform/src/jvmTest/kotlin")
            srcDir("../multiplatform/src/commonTest/kotlin")
        }
    }
}