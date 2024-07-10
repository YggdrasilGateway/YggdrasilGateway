package com.kasukusakura.yggdrasilgateway.api.util

import java.io.ByteArrayOutputStream

public class AccessibleByteArrayOutputStream : ByteArrayOutputStream {
    public constructor() : super()
    public constructor(size: Int) : super(size)


    public var buffer: ByteArray
        get() = super.buf
        set(value) {
            super.buf = value
        }

    public var pointer: Int
        get() = super.count
        set(value) {
            super.count = value
        }
}