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
    fun write(b: ByteArray)
    fun write(b: BytesSlice)

    fun write(b: Bytes) =
        write(b.value)

    fun write(t: Tag) =
        also { write(UInt32(t.value)) }

    fun write(e: KtEnum) =
        write(Int32(e.value))

    fun write(m: KtMessage) {
        write(Int32(m.messageSize))
        m.serialize(this)
    }

    fun writeUnknown(u: UnknownFieldSet) {
        u.unknownFields.forEach { (k, v) -> v.write(k, this) }
    }
}
