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

@file:Suppress("DEPRECATION")

package com.toasttab.protokt

@Deprecated("use v1")
object ApiProto {
    val descriptor: FileDescriptor by lazy {
        val descriptorData = arrayOf(
            "\ngoogle/protobuf/api.protogoogle.prot" +
                "obuf\$google/protobuf/source_context.pro" +
                "togoogle/protobuf/type.proto\"ﾁ\nApi" +
                "\nname (\t(\nmethods (2.google.p" +
                "rotobuf.Method(\noptions (2.google" +
                ".protobuf.Option\nversion (\t6\nsou" +
                "rce_context (2.google.protobuf.Sour" +
                "ceContext&\nmixins (2.google.proto" +
                "buf.Mixin\'\nsyntax (2.google.proto" +
                "buf.Syntax\"ￕ\nMethod\nname (\t\nr" +
                "equest_type_url (\t\nrequest_streami" +
                "ng (\b\nresponse_type_url (\t\nr" +
                "esponse_streaming (\b(\noptions (" +
                "2.google.protobuf.Option\'\nsyntax (" +
                "2.google.protobuf.Syntax\"#\nMixin\nn" +
                "ame (\t\nroot (\tBv\ncom.google.pr" +
                "otobufB\bApiProtoPZ,google.golang.org/pr" +
                "otobuf/types/known/apipbﾢGPBﾪGoogle." +
                "Protobuf.WellKnownTypesbproto3"
        )

        FileDescriptor.buildFrom(
            descriptorData,
            listOf(
                SourceContextProto.descriptor,
                TypeProto.descriptor
            )
        )
    }
}

@Deprecated("use v1")
val Api.Deserializer.descriptor: Descriptor
    get() = ApiProto.descriptor.messageTypes[0]

@Deprecated("use v1")
val Method.Deserializer.descriptor: Descriptor
    get() = ApiProto.descriptor.messageTypes[1]

@Deprecated("use v1")
val Mixin.Deserializer.descriptor: Descriptor
    get() = ApiProto.descriptor.messageTypes[2]
