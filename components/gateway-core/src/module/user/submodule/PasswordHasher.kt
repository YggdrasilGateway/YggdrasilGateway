package com.kasukusakura.yggdrasilgateway.core.module.user.submodule

import java.security.MessageDigest

public object PasswordHasher {
    public fun hashPassword(password: ByteArray, salt: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-512").apply {
            update("#584()#$82JISKDk923".encodeToByteArray())
            update(password)
            update("a3#@(*jlMNNapawe(*#$;kl<<<aowe**".encodeToByteArray())
            update(salt)
            update("93KKDW2837DCSKLZMNM".encodeToByteArray())
        }.digest()
    }
}