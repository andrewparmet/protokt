package protokt.v1.testing

import protokt.v1.configureLong
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BasicSerializationTest {
    @BeforeTest
    fun before() {
        configureLong()
    }

    @Test
    fun test_timestamp_round_trip() {
        val timestamp =
            Timestamp {
                seconds = Long.MAX_VALUE
                nanos = 10
            }

        val deserialized = Timestamp.deserialize(timestamp.serialize())

        assertEquals(timestamp, deserialized)
    }

    @Test
    fun test_small_timestamp_round_trip() {
        val timestamp =
            Timestamp {
                seconds = 15
                nanos = 10
            }

        val deserialized = Timestamp.deserialize(timestamp.serialize())

        assertEquals(timestamp, deserialized)
    }
}
