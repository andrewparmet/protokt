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

package protokt.v1.codegen.util

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import io.grpc.kotlin.generator.GeneratorRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

fun generateGrpcKotlinStubs(
    params: PluginParams,
    request: CodeGeneratorRequest
): List<CodeGeneratorResponse.File> =
    if (
        params.appliedKotlinPlugin in setOf(KotlinPlugin.JVM, KotlinPlugin.ANDROID) &&
        (params.generateGrpc || params.onlyGenerateGrpc) &&
        !params.onlyGenerateGrpcDescriptors
    ) {
        val out = ReadableByteArrayOutputStream()
        GeneratorRunner.mainAsProtocPlugin(stripPackages(request).toByteArray().inputStream(), out)
        CodeGeneratorResponse.parseFrom(out.inputStream()).fileList
    } else {
        emptyList()
    }

private fun stripPackages(request: CodeGeneratorRequest) =
    request.toBuilder()
        .clearProtoFile()
        .addAllProtoFile(
            request.protoFileList.map { fdp ->
                fdp.toBuilder()
                    .setOptions(
                        fdp.options.toBuilder()
                            .setJavaPackage(resolvePackage(fdp))
                            .build()
                    )
                    .build()
            }
        )
        .build()

private class ReadableByteArrayOutputStream : ByteArrayOutputStream() {
    fun inputStream() =
        ByteArrayInputStream(buf, 0, count)
}
