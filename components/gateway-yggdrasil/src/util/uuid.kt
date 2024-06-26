package com.kasukusakura.yggdrasilgateway.yggdrasil.util

import com.kasukusakura.yggdrasilgateway.api.util.toHex
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.buffer.UnpooledByteBufAllocator
import java.util.*

public fun UUID.toStringUnsigned(): String {
    return UnpooledByteBufAllocator.DEFAULT
        .heapBuffer(Long.SIZE_BYTES * 2)
        .writeLong(mostSignificantBits)
        .writeLong(leastSignificantBits)
        .toHex()
}

public fun String.parseUuid(): UUID {
    if (length == 32) {
        val ba = ByteBufUtil.decodeHexDump(this)
        val buf = Unpooled.wrappedBuffer(ba)
        val high = buf.readLong()
        val low = buf.readLong()

        return UUID(high, low)
    }
    return UUID.fromString(this)
}

private fun main() {
    println(UUID.randomUUID().also { println(it) }.toStringUnsigned())
}