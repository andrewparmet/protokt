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

package com.toasttab.protokt.grpc.v1

class MethodDescriptor<ReqT, RespT>(
    val fullMethodName: String,
    val type: MethodType,
    val requestMarshaller: Marshaller<ReqT>,
    val responseMarshaller: Marshaller<RespT>,
    val schemaDescriptor: Any? = null
) {
    enum class MethodType {
        BIDI_STREAMING,
        CLIENT_STREAMING,
        SERVER_STREAMING,
        UNARY,
        UNKNOWN;

        val clientSendsOneMessage
            get() = this == UNARY || this == SERVER_STREAMING

        val serverSendsOneMessage
            get() = this == UNARY || this == CLIENT_STREAMING
    }

    interface Marshaller<T> {
        fun parse(bytes: ByteArray): T

        fun serialize(value: T): dynamic
    }

    internal val serviceName = extractFullServiceName(fullMethodName)
    internal val bareMethodName = extractBareMethodName(fullMethodName)

    override fun toString() =
        "MethodDescriptor(" +
            "name=$fullMethodName, " +
            "type=$type, " +
            "requestMarshaller=$requestMarshaller, " +
            "responseMarshaller=$responseMarshaller, " +
            "schemaDescriptor=$schemaDescriptor)"
}

private fun extractFullServiceName(fullMethodName: String): String? {
    val index = fullMethodName.lastIndexOf('/')
    if (index == -1) {
        return null
    }
    return fullMethodName.substring(0, index)
}

private fun extractBareMethodName(fullMethodName: String): String? {
    val index = fullMethodName.lastIndexOf('/')
    if (index == -1) {
        return null
    }
    return fullMethodName.substring(index + 1)
}
