/*
 * Copyright (c) 2022 Toast Inc.
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

import com.google.protobuf.gradle.protobuf
import com.toasttab.protokt.gradle.protoktExtensions

plugins {
    id("protokt.multiplatform-conventions")
}

localProtokt()
pureKotlin()

kotlin {
    sourceSets {
        val commonMain by getting {}

        val jvmTest by getting {
            dependencies {
                implementation(libraries.protobufJava)
            }
        }

        val jsTest by getting {}

        js(BOTH) {
            browser {
                testTask {
                    useKarma {
                        useFirefoxHeadless()
                    }
                }
            }

            nodejs {
                testTask {
                    useMocha()
                }
            }

            useCommonJs()
        }
    }
}

dependencies {
    protoktExtensions(project(":extensions:protokt-extensions"))

    // TODO: Why doesn't this work? Why must this be a `protobuf` dependency?
    // protoktExtensions(project(":extensions:protokt-extensions"))
    protobuf(files("../../extensions/protokt-extensions-lite/src/main/proto"))
}