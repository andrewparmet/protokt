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

fun sizeof(enum: KtEnum) = sizeofInt32(enum.value)
fun sizeof(msg: KtMessage) = sizeofUInt32(msg.messageSize) + msg.messageSize
fun sizeof(b: Bytes) = CodedOutputStream.computeByteArraySizeNoTag(b.value)
fun sizeof(b: BytesSlice) = sizeofUInt32(b.length) + b.length
fun sizeof(ba: ByteArray) = CodedOutputStream.computeByteArraySizeNoTag(ba)
fun sizeof(s: String) = CodedOutputStream.computeStringSizeNoTag(s)
fun sizeof(b: Boolean) = CodedOutputStream.computeBoolSizeNoTag(b)
fun sizeofInt64(l: Long) = CodedOutputStream.computeInt64SizeNoTag(l)
fun sizeof(d: Double) = CodedOutputStream.computeDoubleSizeNoTag(d)
fun sizeof(f: Float) = CodedOutputStream.computeFloatSizeNoTag(f)
fun sizeofFixed32(i: Int) = CodedOutputStream.computeFixed32SizeNoTag(i)
fun sizeofFixed64(l: Long) = CodedOutputStream.computeFixed64SizeNoTag(l)
fun sizeofSFixed32(i: Int) = CodedOutputStream.computeSFixed32SizeNoTag(i)
fun sizeofSFixed64(l: Long) = CodedOutputStream.computeSFixed64SizeNoTag(l)
fun sizeofInt32(i: Int) = CodedOutputStream.computeInt32SizeNoTag(i)
fun sizeofUInt32(i: Int) = CodedOutputStream.computeUInt32SizeNoTag(i)
fun sizeofSInt32(i: Int) = CodedOutputStream.computeSInt32SizeNoTag(i)
fun sizeofUInt64(l: Long) = CodedOutputStream.computeUInt64SizeNoTag(l)
fun sizeofSint64(l: Long): Int = CodedOutputStream.computeSInt64SizeNoTag(l)
fun sizeof(t: Tag) = CodedOutputStream.computeTagSize(t.value)

fun <K, V> sizeofMap(
    m: Map<K, V>,
    tag: Tag,
    sizeof: (K, V) -> Int
) =
    sizeof(tag).let { t ->
        m.entries.sumBy { (k, v) ->
            t + sizeof(k, v).let {
                s -> s + sizeofUInt32(s)
            }
        }
    }
