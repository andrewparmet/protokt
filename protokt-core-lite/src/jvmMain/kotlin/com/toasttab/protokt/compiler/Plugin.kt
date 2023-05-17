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

package com.toasttab.protokt.compiler

import com.toasttab.protokt.FileDescriptorProto
import com.toasttab.protokt.GeneratedCodeInfo
import com.toasttab.protokt.rt.Int32
import com.toasttab.protokt.rt.KtDeserializer
import com.toasttab.protokt.rt.KtEnum
import com.toasttab.protokt.rt.KtEnumDeserializer
import com.toasttab.protokt.rt.KtGeneratedMessage
import com.toasttab.protokt.rt.KtMessageDeserializer
import com.toasttab.protokt.rt.KtMessageSerializer
import com.toasttab.protokt.rt.Tag
import com.toasttab.protokt.rt.UInt64
import com.toasttab.protokt.rt.UnknownFieldSet
import com.toasttab.protokt.rt.copyList
import com.toasttab.protokt.rt.finishList
import com.toasttab.protokt.rt.sizeof
import com.toasttab.protokt.v1.AbstractKtMessage
import com.toasttab.protokt.v1.NewToOldAdapter
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList

/**
 * The version number of protocol compiler.
 */
@KtGeneratedMessage("google.protobuf.compiler.Version")
@com.toasttab.protokt.v1.KtGeneratedMessage("google.protobuf.compiler.Version")
class Version private constructor(
    val major: Int?,
    val minor: Int?,
    val patch: Int?,
    /**
     * A suffix for alpha, beta or rc release, e.g., "alpha-1", "rc2". It should be empty for
     * mainline stable releases.
     */
    val suffix: String?,
    val unknownFields: UnknownFieldSet = UnknownFieldSet.empty(),
) : AbstractKtMessage() {
    override val messageSize: Int by lazy { messageSize() }

    private fun messageSize(): Int {
        var result = 0
        if (major != null) {
            result += sizeof(Tag(1)) + sizeof(Int32(major))
        }
        if (minor != null) {
            result += sizeof(Tag(2)) + sizeof(Int32(minor))
        }
        if (patch != null) {
            result += sizeof(Tag(3)) + sizeof(Int32(patch))
        }
        if (suffix != null) {
            result += sizeof(Tag(4)) + sizeof(suffix)
        }
        result += unknownFields.size()
        return result
    }

    override fun serialize(serializer: com.toasttab.protokt.v1.KtMessageSerializer) {
        val adapter = NewToOldAdapter(serializer)
        if (major != null) {
            adapter.write(Tag(8)).write(Int32(major))
        }
        if (minor != null) {
            adapter.write(Tag(16)).write(Int32(minor))
        }
        if (patch != null) {
            adapter.write(Tag(24)).write(Int32(patch))
        }
        if (suffix != null) {
            adapter.write(Tag(34)).write(suffix)
        }
        adapter.writeUnknown(unknownFields)
    }

    override fun equals(other: Any?): Boolean = other is Version &&
        other.major == major &&
        other.minor == minor &&
        other.patch == patch &&
        other.suffix == suffix &&
        other.unknownFields == unknownFields

    override fun hashCode(): Int {
        var result = unknownFields.hashCode()
        result = 31 * result + major.hashCode()
        result = 31 * result + minor.hashCode()
        result = 31 * result + patch.hashCode()
        result = 31 * result + suffix.hashCode()
        return result
    }

    override fun toString(): String = "Version(" +
        "major=$major, " +
        "minor=$minor, " +
        "patch=$patch, " +
        "suffix=$suffix" +
        "${if (unknownFields.isEmpty()) "" else ", unknownFields=$unknownFields"})"

    fun copy(dsl: VersionDsl.() -> Unit): Version = Version.Deserializer {
        major = this@Version.major
        minor = this@Version.minor
        patch = this@Version.patch
        suffix = this@Version.suffix
        unknownFields = this@Version.unknownFields
        dsl()
    }

    class VersionDsl {
        var major: Int? = null

        var minor: Int? = null

        var patch: Int? = null

        var suffix: String? = null

        var unknownFields: UnknownFieldSet = UnknownFieldSet.empty()

        fun build(): Version = Version(
            major,
            minor,
            patch,
            suffix,
            unknownFields
        )
    }

    companion object Deserializer :
        KtDeserializer<Version>,
        (VersionDsl.() -> Unit) -> Version {
        override fun deserialize(deserializer: KtMessageDeserializer): Version {
            var major: Int? = null
            var minor: Int? = null
            var patch: Int? = null
            var suffix: String? = null
            var unknownFields: UnknownFieldSet.Builder? = null
            while (true) {
                when (deserializer.readTag()) {
                    0 -> return Version(
                        major,
                        minor,
                        patch,
                        suffix,
                        UnknownFieldSet.from(unknownFields)
                    )
                    8 -> major = deserializer.readInt32()
                    16 -> minor = deserializer.readInt32()
                    24 -> patch = deserializer.readInt32()
                    34 -> suffix = deserializer.readString()
                    else -> unknownFields = (
                        unknownFields
                            ?: UnknownFieldSet.Builder()
                        ).also { it.add(deserializer.readUnknown()) }
                }
            }
        }

        override fun invoke(dsl: VersionDsl.() -> Unit): Version =
            VersionDsl().apply(dsl).build()
    }
}

/**
 * An encoded CodeGeneratorRequest is written to the plugin's stdin.
 */
@KtGeneratedMessage("google.protobuf.compiler.CodeGeneratorRequest")
@com.toasttab.protokt.v1.KtGeneratedMessage("google.protobuf.compiler.CodeGeneratorRequest")
class CodeGeneratorRequest private constructor(
    /**
     * The .proto files that were explicitly listed on the command-line.  The code generator should
     * generate code only for these files.  Each file's descriptor will be included in proto_file,
     * below.
     */
    val fileToGenerate: List<String>,
    /**
     * The generator parameter passed on the command-line.
     */
    val parameter: String?,
    /**
     * The version number of protocol compiler.
     */
    val compilerVersion: Version?,
    /**
     * FileDescriptorProtos for all files in files_to_generate and everything they import.  The
     * files will appear in topological order, so each file appears before any file that imports it.
     *
     *  protoc guarantees that all proto_files will be written after the fields above, even though
     * this is not technically guaranteed by the protobuf wire format.  This theoretically could allow
     * a plugin to stream in the FileDescriptorProtos and handle them one by one rather than read the
     * entire set into memory at once.  However, as of this writing, this is not similarly optimized on
     * protoc's end -- it will store all fields in memory at once before sending them to the plugin.
     *
     *  Type names of fields and extensions in the FileDescriptorProto are always fully qualified.
     */
    val protoFile: List<FileDescriptorProto>,
    val unknownFields: UnknownFieldSet = UnknownFieldSet.empty(),
) : AbstractKtMessage() {
    override val messageSize: Int by lazy { messageSize() }

    private fun messageSize(): Int {
        var result = 0
        if (fileToGenerate.isNotEmpty()) {
            result += (sizeof(Tag(1)) * fileToGenerate.size) + fileToGenerate.sumOf { sizeof(it) }
        }
        if (parameter != null) {
            result += sizeof(Tag(2)) + sizeof(parameter)
        }
        if (compilerVersion != null) {
            result += sizeof(Tag(3)) + sizeof(compilerVersion)
        }
        if (protoFile.isNotEmpty()) {
            result += (sizeof(Tag(15)) * protoFile.size) + protoFile.sumOf { sizeof(it) }
        }
        result += unknownFields.size()
        return result
    }

    override fun serialize(serializer: com.toasttab.protokt.v1.KtMessageSerializer) {
        val adapter = NewToOldAdapter(serializer)
        if (fileToGenerate.isNotEmpty()) {
            fileToGenerate.forEach { adapter.write(Tag(10)).write(it) }
        }
        if (parameter != null) {
            adapter.write(Tag(18)).write(parameter)
        }
        if (compilerVersion != null) {
            adapter.write(Tag(26)).write(compilerVersion)
        }
        if (protoFile.isNotEmpty()) {
            protoFile.forEach { adapter.write(Tag(122)).write(it) }
        }
        adapter.writeUnknown(unknownFields)
    }

    override fun equals(other: Any?): Boolean = other is CodeGeneratorRequest &&
        other.fileToGenerate == fileToGenerate &&
        other.parameter == parameter &&
        other.compilerVersion == compilerVersion &&
        other.protoFile == protoFile &&
        other.unknownFields == unknownFields

    override fun hashCode(): Int {
        var result = unknownFields.hashCode()
        result = 31 * result + fileToGenerate.hashCode()
        result = 31 * result + parameter.hashCode()
        result = 31 * result + compilerVersion.hashCode()
        result = 31 * result + protoFile.hashCode()
        return result
    }

    override fun toString(): String = "CodeGeneratorRequest(" +
        "fileToGenerate=$fileToGenerate, " +
        "parameter=$parameter, " +
        "compilerVersion=$compilerVersion, " +
        "protoFile=$protoFile" +
        "${if (unknownFields.isEmpty()) "" else ", unknownFields=$unknownFields"})"

    fun copy(dsl: CodeGeneratorRequestDsl.() -> Unit): CodeGeneratorRequest =
        CodeGeneratorRequest.Deserializer {
            fileToGenerate = this@CodeGeneratorRequest.fileToGenerate
            parameter = this@CodeGeneratorRequest.parameter
            compilerVersion = this@CodeGeneratorRequest.compilerVersion
            protoFile = this@CodeGeneratorRequest.protoFile
            unknownFields = this@CodeGeneratorRequest.unknownFields
            dsl()
        }

    class CodeGeneratorRequestDsl {
        var fileToGenerate: List<String> = emptyList()
            set(newValue) {
                field = copyList(newValue)
            }

        var parameter: String? = null

        var compilerVersion: Version? = null

        var protoFile: List<FileDescriptorProto> = emptyList()
            set(newValue) {
                field = copyList(newValue)
            }

        var unknownFields: UnknownFieldSet = UnknownFieldSet.empty()

        fun build(): CodeGeneratorRequest = CodeGeneratorRequest(
            finishList(fileToGenerate),
            parameter,
            compilerVersion,
            finishList(protoFile),
            unknownFields
        )
    }

    companion object Deserializer :
        KtDeserializer<CodeGeneratorRequest>,
        (CodeGeneratorRequestDsl.() -> Unit) -> CodeGeneratorRequest {
        override fun deserialize(deserializer: KtMessageDeserializer): CodeGeneratorRequest {
            var fileToGenerate: MutableList<String>? = null
            var parameter: String? = null
            var compilerVersion: Version? = null
            var protoFile: MutableList<FileDescriptorProto>? = null
            var unknownFields: UnknownFieldSet.Builder? = null
            while (true) {
                when (deserializer.readTag()) {
                    0 -> return CodeGeneratorRequest(
                        finishList(fileToGenerate),
                        parameter,
                        compilerVersion,
                        finishList(protoFile),
                        UnknownFieldSet.from(unknownFields)
                    )
                    10 -> fileToGenerate = (fileToGenerate ?: mutableListOf()).apply {
                        deserializer.readRepeated(false) {
                            add(deserializer.readString())
                        }
                    }
                    18 -> parameter = deserializer.readString()
                    26 ->
                        compilerVersion =
                            deserializer.readMessage(com.toasttab.protokt.compiler.Version)
                    122 -> protoFile = (protoFile ?: mutableListOf()).apply {
                        deserializer.readRepeated(false) {
                            add(deserializer.readMessage(com.toasttab.protokt.FileDescriptorProto))
                        }
                    }
                    else -> unknownFields = (
                        unknownFields
                            ?: UnknownFieldSet.Builder()
                        ).also { it.add(deserializer.readUnknown()) }
                }
            }
        }

        override fun invoke(dsl: CodeGeneratorRequestDsl.() -> Unit): CodeGeneratorRequest =
            CodeGeneratorRequestDsl().apply(dsl).build()
    }
}

/**
 * The plugin writes an encoded CodeGeneratorResponse to stdout.
 */
@KtGeneratedMessage("google.protobuf.compiler.CodeGeneratorResponse")
@com.toasttab.protokt.v1.KtGeneratedMessage("google.protobuf.compiler.CodeGeneratorResponse")
class CodeGeneratorResponse private constructor(
    /**
     * Error message.  If non-empty, code generation failed.  The plugin process should exit with
     * status code zero even if it reports an error in this way.
     *
     *  This should be used to indicate errors in .proto files which prevent the code generator from
     * generating correct code.  Errors which indicate a problem in protoc itself -- such as the input
     * CodeGeneratorRequest being unparseable -- should be reported by writing a message to stderr and
     * exiting with a non-zero status code.
     */
    val error: String?,
    /**
     * A bitmask of supported features that the code generator supports. This is a bitwise "or" of
     * values from the Feature enum.
     */
    val supportedFeatures: Long?,
    val `file`: List<File>,
    val unknownFields: UnknownFieldSet = UnknownFieldSet.empty(),
) : AbstractKtMessage() {
    override val messageSize: Int by lazy { messageSize() }

    private fun messageSize(): Int {
        var result = 0
        if (error != null) {
            result += sizeof(Tag(1)) + sizeof(error)
        }
        if (supportedFeatures != null) {
            result += sizeof(Tag(2)) + sizeof(UInt64(supportedFeatures))
        }
        if (file.isNotEmpty()) {
            result += (sizeof(Tag(15)) * file.size) + file.sumOf { sizeof(it) }
        }
        result += unknownFields.size()
        return result
    }

    override fun serialize(serializer: com.toasttab.protokt.v1.KtMessageSerializer) {
        val adapter = NewToOldAdapter(serializer)
        if (error != null) {
            adapter.write(Tag(10)).write(error)
        }
        if (supportedFeatures != null) {
            adapter.write(Tag(16)).write(UInt64(supportedFeatures))
        }
        if (file.isNotEmpty()) {
            file.forEach { adapter.write(Tag(122)).write(it) }
        }
        adapter.writeUnknown(unknownFields)
    }

    override fun equals(other: Any?): Boolean = other is CodeGeneratorResponse &&
        other.error == error &&
        other.supportedFeatures == supportedFeatures &&
        other.file == file &&
        other.unknownFields == unknownFields

    override fun hashCode(): Int {
        var result = unknownFields.hashCode()
        result = 31 * result + error.hashCode()
        result = 31 * result + supportedFeatures.hashCode()
        result = 31 * result + file.hashCode()
        return result
    }

    override fun toString(): String = "CodeGeneratorResponse(" +
        "error=$error, " +
        "supportedFeatures=$supportedFeatures, " +
        "file=$file" +
        "${if (unknownFields.isEmpty()) "" else ", unknownFields=$unknownFields"})"

    fun copy(dsl: CodeGeneratorResponseDsl.() -> Unit): CodeGeneratorResponse =
        CodeGeneratorResponse.Deserializer {
            error = this@CodeGeneratorResponse.error
            supportedFeatures = this@CodeGeneratorResponse.supportedFeatures
            file = this@CodeGeneratorResponse.file
            unknownFields = this@CodeGeneratorResponse.unknownFields
            dsl()
        }

    class CodeGeneratorResponseDsl {
        var error: String? = null

        var supportedFeatures: Long? = null

        var `file`: List<File> = emptyList()
            set(newValue) {
                field = copyList(newValue)
            }

        var unknownFields: UnknownFieldSet = UnknownFieldSet.empty()

        fun build(): CodeGeneratorResponse = CodeGeneratorResponse(
            error,
            supportedFeatures,
            finishList(file),
            unknownFields
        )
    }

    companion object Deserializer :
        KtDeserializer<CodeGeneratorResponse>,
        (CodeGeneratorResponseDsl.() -> Unit) -> CodeGeneratorResponse {
        override fun deserialize(deserializer: KtMessageDeserializer):
            CodeGeneratorResponse {
            var error: String? = null
            var supportedFeatures: Long? = null
            var file: MutableList<File>? = null
            var unknownFields: UnknownFieldSet.Builder? = null
            while (true) {
                when (deserializer.readTag()) {
                    0 -> return CodeGeneratorResponse(
                        error,
                        supportedFeatures,
                        finishList(file),
                        UnknownFieldSet.from(unknownFields)
                    )
                    10 -> error = deserializer.readString()
                    16 -> supportedFeatures = deserializer.readUInt64()
                    122 -> file = (file ?: mutableListOf()).apply {
                        deserializer.readRepeated(false) {
                            add(deserializer.readMessage(com.toasttab.protokt.compiler.CodeGeneratorResponse.File))
                        }
                    }
                    else -> unknownFields = (
                        unknownFields
                            ?: UnknownFieldSet.Builder()
                        ).also { it.add(deserializer.readUnknown()) }
                }
            }
        }

        override fun invoke(dsl: CodeGeneratorResponseDsl.() -> Unit): CodeGeneratorResponse =
            CodeGeneratorResponseDsl().apply(dsl).build()
    }

    /**
     * Sync with code_generator.h.
     */
    sealed class Feature(
        override val `value`: Int,
        override val name: String,
    ) : KtEnum() {
        object NONE : Feature(0, "NONE")

        object PROTO3_OPTIONAL : Feature(1, "PROTO3_OPTIONAL")

        class UNRECOGNIZED(
            `value`: Int,
        ) : Feature(value, "UNRECOGNIZED")

        companion object Deserializer : KtEnumDeserializer<Feature> {
            override fun from(`value`: Int): Feature = when (value) {
                0 -> NONE
                1 -> PROTO3_OPTIONAL
                else -> UNRECOGNIZED(value)
            }
        }
    }

    /**
     * Represents a single generated file.
     */
    @KtGeneratedMessage("google.protobuf.compiler.File")
    @com.toasttab.protokt.v1.KtGeneratedMessage("google.protobuf.compiler.File")
    class File private constructor(
        /**
         * The file name, relative to the output directory.  The name must not contain "." or ".."
         * components and must be relative, not be absolute (so, the file cannot lie outside the output
         * directory).  "/" must be used as the path separator, not "\".
         *
         *  If the name is omitted, the content will be appended to the previous file.  This allows
         * the generator to break large files into small chunks, and allows the generated text to be
         * streamed back to protoc so that large files need not reside completely in memory at one
         * time.  Note that as of this writing protoc does not optimize for this -- it will read the
         * entire CodeGeneratorResponse before writing files to disk.
         */
        val name: String?,
        /**
         * If non-empty, indicates that the named file should already exist, and the content here is
         * to be inserted into that file at a defined insertion point.  This feature allows a code
         * generator to extend the output produced by another code generator.  The original generator
         * may provide insertion points by placing special annotations in the file that look like:
         * @@protoc_insertion_point(NAME) The annotation can have arbitrary text before and after it on
         * the line, which allows it to be placed in a comment.  NAME should be replaced with an
         * identifier naming the point -- this is what other generators will use as the
         * insertion_point.  Code inserted at this point will be placed immediately above the line
         * containing the insertion point (thus multiple insertions to the same point will come out in
         * the order they were added). The double-@ is intended to make it unlikely that the generated
         * code could contain things that look like insertion points by accident.
         *
         *  For example, the C++ code generator places the following line in the .pb.h files that it
         * generates:   // @@protoc_insertion_point(namespace_scope) This line appears within the scope
         * of the file's package namespace, but outside of any particular class.  Another plugin can
         * then specify the insertion_point "namespace_scope" to generate additional classes or other
         * declarations that should be placed in this scope.
         *
         *  Note that if the line containing the insertion point begins with whitespace, the same
         * whitespace will be added to every line of the inserted text.  This is useful for languages
         * like Python, where indentation matters.  In these languages, the insertion point comment
         * should be indented the same amount as any inserted code will need to be in order to work
         * correctly in that context.
         *
         *  The code generator that generates the initial file and the one which inserts into it
         * must both run as part of a single invocation of protoc. Code generators are executed in the
         * order in which they appear on the command line.
         *
         *  If |insertion_point| is present, |name| must also be present.
         */
        val insertionPoint: String?,
        /**
         * The file contents.
         */
        val content: String?,
        /**
         * Information describing the file content being inserted. If an insertion point is used,
         * this information will be appropriately offset and inserted into the code generation metadata
         * for the generated files.
         */
        val generatedCodeInfo: GeneratedCodeInfo?,
        val unknownFields: UnknownFieldSet = UnknownFieldSet.empty(),
    ) : AbstractKtMessage() {
        override val messageSize: Int by lazy { messageSize() }

        private fun messageSize(): Int {
            var result = 0
            if (name != null) {
                result += sizeof(Tag(1)) + sizeof(name)
            }
            if (insertionPoint != null) {
                result += sizeof(Tag(2)) + sizeof(insertionPoint)
            }
            if (content != null) {
                result += sizeof(Tag(15)) + sizeof(content)
            }
            if (generatedCodeInfo != null) {
                result += sizeof(Tag(16)) + sizeof(generatedCodeInfo)
            }
            result += unknownFields.size()
            return result
        }

        override fun serialize(serializer: com.toasttab.protokt.v1.KtMessageSerializer) {
            val adapter = NewToOldAdapter(serializer)
            if (name != null) {
                adapter.write(Tag(10)).write(name)
            }
            if (insertionPoint != null) {
                adapter.write(Tag(18)).write(insertionPoint)
            }
            if (content != null) {
                adapter.write(Tag(122)).write(content)
            }
            if (generatedCodeInfo != null) {
                adapter.write(Tag(130)).write(generatedCodeInfo)
            }
            adapter.writeUnknown(unknownFields)
        }

        override fun equals(other: Any?): Boolean = other is File &&
            other.name == name &&
            other.insertionPoint == insertionPoint &&
            other.content == content &&
            other.generatedCodeInfo == generatedCodeInfo &&
            other.unknownFields == unknownFields

        override fun hashCode(): Int {
            var result = unknownFields.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + insertionPoint.hashCode()
            result = 31 * result + content.hashCode()
            result = 31 * result + generatedCodeInfo.hashCode()
            return result
        }

        override fun toString(): String = "File(" +
            "name=$name, " +
            "insertionPoint=$insertionPoint, " +
            "content=$content, " +
            "generatedCodeInfo=$generatedCodeInfo" +
            "${if (unknownFields.isEmpty()) "" else ", unknownFields=$unknownFields"})"

        fun copy(dsl: FileDsl.() -> Unit): File = File.Deserializer {
            name = this@File.name
            insertionPoint = this@File.insertionPoint
            content = this@File.content
            generatedCodeInfo = this@File.generatedCodeInfo
            unknownFields = this@File.unknownFields
            dsl()
        }

        class FileDsl {
            var name: String? = null

            var insertionPoint: String? = null

            var content: String? = null

            var generatedCodeInfo: GeneratedCodeInfo? = null

            var unknownFields: UnknownFieldSet = UnknownFieldSet.empty()

            fun build(): File = File(
                name,
                insertionPoint,
                content,
                generatedCodeInfo,
                unknownFields
            )
        }

        companion object Deserializer : KtDeserializer<File>, (FileDsl.() -> Unit) -> File {
            override fun deserialize(deserializer: KtMessageDeserializer): File {
                var name: String? = null
                var insertionPoint: String? = null
                var content: String? = null
                var generatedCodeInfo: GeneratedCodeInfo? = null
                var unknownFields: UnknownFieldSet.Builder? = null
                while (true) {
                    when (deserializer.readTag()) {
                        0 -> return File(
                            name,
                            insertionPoint,
                            content,
                            generatedCodeInfo,
                            UnknownFieldSet.from(unknownFields)
                        )
                        10 -> name = deserializer.readString()
                        18 -> insertionPoint = deserializer.readString()
                        122 -> content = deserializer.readString()
                        130 ->
                            generatedCodeInfo =
                                deserializer.readMessage(com.toasttab.protokt.GeneratedCodeInfo)
                        else -> unknownFields = (
                            unknownFields
                                ?: UnknownFieldSet.Builder()
                            ).also {
                            it.add(deserializer.readUnknown())
                        }
                    }
                }
            }

            override fun invoke(dsl: FileDsl.() -> Unit): File = FileDsl().apply(dsl).build()
        }
    }
}