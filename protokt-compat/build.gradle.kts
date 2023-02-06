/*
 * Copyright (c) 2023 Toast Inc.
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
import com.toasttab.protokt.gradle.protokt

plugins {
    id("protokt.jvm-conventions")
    kotlin("kapt")
}

localProtokt()
enablePublishing()
compatibleWithAndroid()
trackKotlinApiCompatibility()

protokt {
    lite = true
    backwardsCompatibilityMode = true
}

dependencies {
    protobuf(libs.protobufJava)

    api(project(":extensions:protokt-extensions-api"))

    compileOnly(libs.protobufJava)
}
