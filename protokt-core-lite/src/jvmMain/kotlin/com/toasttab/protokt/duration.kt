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

// Generated by protokt version 0.10.2. Do not modify.
// Source: google/protobuf/duration.proto
package com.toasttab.protokt

import com.toasttab.protokt.rt.Int32
import com.toasttab.protokt.rt.Int64
import com.toasttab.protokt.rt.KtDeserializer
import com.toasttab.protokt.rt.KtGeneratedMessage
import com.toasttab.protokt.rt.KtMessageDeserializer
import com.toasttab.protokt.rt.Tag
import com.toasttab.protokt.rt.UnknownFieldSet
import com.toasttab.protokt.rt.sizeof
import protokt.v1.AbstractKtMessage
import protokt.v1.NewToOldAdapter
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Unit

/**
 * A Duration represents a signed, fixed-length span of time represented as a count of seconds and
 * fractions of seconds at nanosecond resolution. It is independent of any calendar and concepts like
 * "day" or "month". It is related to Timestamp in that the difference between two Timestamp values is
 * a Duration and it can be added or subtracted from a Timestamp. Range is approximately +-10,000
 * years.
 *
 *  # Examples
 *
 *  Example 1: Compute Duration from two Timestamps in pseudo code.
 *
 *      Timestamp start = ...;     Timestamp end = ...;     Duration duration = ...;
 *
 *      duration.seconds = end.seconds - start.seconds;     duration.nanos = end.nanos -
 * start.nanos;
 *
 *      if (duration.seconds < 0 && duration.nanos > 0) {       duration.seconds += 1;
 * duration.nanos -= 1000000000;     } else if (duration.seconds > 0 && duration.nanos < 0) {
 * duration.seconds -= 1;       duration.nanos += 1000000000;     }
 *
 *  Example 2: Compute Timestamp from Timestamp + Duration in pseudo code.
 *
 *      Timestamp start = ...;     Duration duration = ...;     Timestamp end = ...;
 *
 *      end.seconds = start.seconds + duration.seconds;     end.nanos = start.nanos +
 * duration.nanos;
 *
 *      if (end.nanos < 0) {       end.seconds -= 1;       end.nanos += 1000000000;     } else if
 * (end.nanos >= 1000000000) {       end.seconds += 1;       end.nanos -= 1000000000;     }
 *
 *  Example 3: Compute Duration from datetime.timedelta in Python.
 *
 *      td = datetime.timedelta(days=3, minutes=10)     duration = Duration()
 * duration.FromTimedelta(td)
 *
 *  # JSON Mapping
 *
 *  In JSON format, the Duration type is encoded as a string rather than an object, where the string
 * ends in the suffix "s" (indicating seconds) and is preceded by the number of seconds, with
 * nanoseconds expressed as fractional seconds. For example, 3 seconds with 0 nanoseconds should be
 * encoded in JSON format as "3s", while 3 seconds and 1 nanosecond should be expressed in JSON format
 * as "3.000000001s", and 3 seconds and 1 microsecond should be expressed in JSON format as
 * "3.000001s".
 *
 *
 */
@KtGeneratedMessage("google.protobuf.Duration")
@protokt.v1.KtGeneratedMessage("google.protobuf.Duration")
@Deprecated("use v1")
class Duration private constructor(
    /**
     * Signed seconds of the span of time. Must be from -315,576,000,000 to +315,576,000,000
     * inclusive. Note: these bounds are computed from: 60 sec/min * 60 min/hr * 24 hr/day * 365.25
     * days/year * 10000 years
     */
    val seconds: Long,
    /**
     * Signed fractions of a second at nanosecond resolution of the span of time. Durations less
     * than one second are represented with a 0 `seconds` field and a positive or negative `nanos`
     * field. For durations of one second or more, a non-zero value for the `nanos` field must be of
     * the same sign as the `seconds` field. Must be from -999,999,999 to +999,999,999 inclusive.
     */
    val nanos: Int,
    val unknownFields: UnknownFieldSet = UnknownFieldSet.empty(),
) : AbstractKtMessage() {
    override val messageSize: Int by lazy { messageSize() }

    private fun messageSize(): Int {
        var result = 0
        if (seconds != 0L) {
            result += sizeof(Tag(1)) + sizeof(Int64(seconds))
        }
        if (nanos != 0) {
            result += sizeof(Tag(2)) + sizeof(Int32(nanos))
        }
        result += unknownFields.size()
        return result
    }

    override fun serialize(serializer: protokt.v1.KtMessageSerializer) {
        val adapter = NewToOldAdapter(serializer)
        if (seconds != 0L) {
            adapter.write(Tag(8)).write(Int64(seconds))
        }
        if (nanos != 0) {
            adapter.write(Tag(16)).write(Int32(nanos))
        }
        adapter.writeUnknown(unknownFields)
    }

    override fun equals(other: Any?): Boolean = other is Duration &&
        other.seconds == seconds &&
        other.nanos == nanos &&
        other.unknownFields == unknownFields

    override fun hashCode(): Int {
        var result = unknownFields.hashCode()
        result = 31 * result + seconds.hashCode()
        result = 31 * result + nanos.hashCode()
        return result
    }

    override fun toString(): String = "Duration(" +
        "seconds=$seconds, " +
        "nanos=$nanos" +
        "${if (unknownFields.isEmpty()) "" else ", unknownFields=$unknownFields"})"

    fun copy(dsl: DurationDsl.() -> Unit): Duration = Duration.Deserializer {
        seconds = this@Duration.seconds
        nanos = this@Duration.nanos
        unknownFields = this@Duration.unknownFields
        dsl()
    }

    class DurationDsl {
        var seconds: Long = 0L

        var nanos: Int = 0

        var unknownFields: UnknownFieldSet = UnknownFieldSet.empty()

        fun build(): Duration = Duration(seconds,
            nanos,
            unknownFields)
    }

    companion object Deserializer : KtDeserializer<Duration>,
            (DurationDsl.() -> Unit) -> Duration {
        override fun deserialize(deserializer: KtMessageDeserializer): Duration {
            var seconds = 0L
            var nanos = 0
            var unknownFields: UnknownFieldSet.Builder? = null
            while (true) {
                when(deserializer.readTag()) {
                    0 -> return Duration(seconds,
                        nanos,
                        UnknownFieldSet.from(unknownFields))
                    8 -> seconds = deserializer.readInt64()
                    16 -> nanos = deserializer.readInt32()
                    else -> unknownFields = (unknownFields ?:
                    UnknownFieldSet.Builder()).also {it.add(deserializer.readUnknown()) }
                }
            }
        }

        override fun invoke(dsl: DurationDsl.() -> Unit): Duration =
            DurationDsl().apply(dsl).build()
    }
}
