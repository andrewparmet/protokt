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
object TimestampProto {
    val descriptor: FileDescriptor by lazy {
        val descriptorData = arrayOf(
            "\ngoogle/protobuf/timestamp.protogoogl" +
                "e.protobuf\"+\n\tTimestamp\nseconds (" +
                "\r\nnanos (Bﾅ\ncom.google.protobufB" +
                "TimestampProtoPZ2google.golang.org/pro" +
                "tobuf/types/known/timestamppb￸ﾢGPBﾪ" +
                "Google.Protobuf.WellKnownTypesbproto3"
        )

        FileDescriptor.buildFrom(
            descriptorData,
            listOf()
        )
    }
}

@Deprecated("use v1")
val Timestamp.Deserializer.descriptor: Descriptor
    get() = TimestampProto.descriptor.messageTypes[0]
