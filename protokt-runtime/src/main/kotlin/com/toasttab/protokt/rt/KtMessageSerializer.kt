/*
 * Copyright (c) 2019 Toast Inc.
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

package com.toasttab.protokt.rt

import com.google.protobuf.CodedOutputStream

interface KtMessageSerializer {
    fun writeFixed32(i: Int)
    fun writeSFixed32(i: Int)
    fun writeUInt32(i: Int)
    fun writeSInt32(i: Int)
    fun writeInt32(i: Int)
    fun writeFixed64(l: Long)
    fun writeSFixed64(l: Long)
    fun writeUInt64(l: Long)
    fun writeSInt64(l: Long)
    fun writeInt64(l: Long)
    fun write(f: Float)
    fun write(d: Double)
    fun write(s: String)
    fun write(b: Boolean)
    fun write(b: Bytes) = write(b.value)
    fun write(b: BytesSlice)
    fun write(b: ByteArray)
    fun write(e: KtEnum)
    fun write(m: KtMessage)
    fun write(t: Tag): KtMessageSerializer
    fun writeUnknown(u: UnknownFieldSet)
}

fun serializer(stream: CodedOutputStream): KtMessageSerializer {
    return object : KtMessageSerializer {
        override fun writeFixed32(i: Int) =
            stream.writeFixed32NoTag(i)

        override fun writeSFixed32(i: Int) =
            stream.writeSFixed32NoTag(i)

        override fun writeUInt32(i: Int) =
            stream.writeUInt32NoTag(i)

        override fun writeSInt32(i: Int) =
            stream.writeSInt32NoTag(i)

        override fun writeInt32(i: Int) =
            stream.writeInt32NoTag(i)

        override fun writeFixed64(l: Long) =
            stream.writeFixed64NoTag(l)

        override fun writeSFixed64(l: Long) =
            stream.writeSFixed64NoTag(l)

        override fun writeUInt64(l: Long) =
            stream.writeUInt64NoTag(l)

        override fun writeSInt64(l: Long) =
            stream.writeSInt64NoTag(l)

        override fun writeInt64(l: Long) =
            stream.writeInt64NoTag(l)

        override fun write(b: Boolean) =
            stream.writeBoolNoTag(b)

        override fun write(s: String) =
            stream.writeStringNoTag(s)

        override fun write(f: Float) =
            stream.writeFloatNoTag(f)

        override fun write(d: Double) =
            stream.writeDoubleNoTag(d)

        override fun write(b: ByteArray) =
            stream.writeByteArrayNoTag(b)

        override fun write(e: KtEnum) =
            stream.writeInt32NoTag(e.value)

        override fun write(m: KtMessage) {
            stream.writeUInt32NoTag(m.messageSize)
            m.serialize(this)
        }

        override fun write(b: BytesSlice) {
            stream.writeUInt32NoTag(b.length)
            stream.write(b.array, b.offset, b.length)
        }

        override fun writeUnknown(u: UnknownFieldSet) {
            u.unknownFields.forEach { (k, v) -> v.write(k, this) }
        }

        override fun write(t: Tag) =
            also { stream.writeUInt32NoTag(t.value) }
    }
}
