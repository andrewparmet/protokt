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

interface KtMessageSerializer {
    fun write(i: Fixed32)
    fun write(i: SFixed32)
    fun write(i: UInt32)
    fun write(i: SInt32)
    fun write(i: Int32)
    fun write(l: Fixed64)
    fun write(l: SFixed64)
    fun write(l: UInt64)
    fun write(l: SInt64)
    fun write(l: Int64)
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

expect fun KtMessage.serialize(): ByteArray