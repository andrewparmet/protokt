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

package com.toasttab.protokt.testing.js

import com.toasttab.protokt.Timestamp
import kotlin.test.Test
import kotlin.test.assertEquals

class BasicSerializationTest {
    @Test
    fun test_timestamp_round_trip() {
        val timestamp =
            Timestamp.TimestampDsl().apply {
                seconds = Long.MAX_VALUE
                nanos = 10
            }.build()

        val deserialized = Timestamp.deserialize(timestamp.serialize())

        assertEquals(timestamp, deserialized)
    }

    @Test
    fun test_small_timestamp_round_trip() {
        val timestamp =
            Timestamp.TimestampDsl().apply {
                seconds = 15
                nanos = 10
            }.build()

        val deserialized = Timestamp.deserialize(timestamp.serialize())

        assertEquals(timestamp, deserialized)
    }
}
