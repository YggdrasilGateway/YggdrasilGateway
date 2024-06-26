package com.kasukusakura.yggdrasilgateway.api.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil

public fun ByteBuf.toHex(): String {
    return ByteBufUtil.hexDump(this)
}