package com.kasukusakura.yggdrasilgateway.api.util

import java.util.*

public fun CharSequence.decodeHex(hex: HexFormat = HexFormat.of()): ByteArray = hex.parseHex(this)
public fun ByteArray.encodeHex(hex: HexFormat = HexFormat.of()): String = hex.formatHex(this)