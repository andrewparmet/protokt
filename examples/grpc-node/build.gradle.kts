/*
 * Copyright (c) 2023 Toast, Inc.
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
import com.toasttab.protokt.v1.gradle.protoktExtensions

plugins {
    id("org.jetbrains.kotlin.js")
}

kotlin {
    js(IR) {
        nodejs {
            testTask {
                useMocha()
            }
        }
        binaries.executable()
        useCommonJs()
    }
}

localProtokt()

dependencies {
    protoktExtensions(project(":extensions:protokt-extensions"))

    implementation(npm("@grpc/grpc-js", libs.versions.grpcJs.get()))

    testImplementation(kotlin("test"))
}

sourceSets {
    named("jsMain") {
        proto {
            srcDir("../protos/src/main/proto")
        }
    }
}