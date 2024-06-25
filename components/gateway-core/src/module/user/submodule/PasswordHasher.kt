package com.kasukusakura.yggdrasilgateway.core.module.user.submodule

import java.security.MessageDigest

public object PasswordHasher {
    public fun hashPassword(
        password: ByteArray,
        salt: ByteArray,
        passwordHashed: Boolean = false,
    ): ByteArray {
        val hpwd: ByteArray = if (passwordHashed) {
            password
        } else {
            MessageDigest.getInstance("SHA-512").digest(password)
        }

        return MessageDigest.getInstance("SHA-512").apply {
            update("#584()#$82JISKDk923".encodeToByteArray())
            update(hpwd)
            update("a3#@(*jlMNNapawe(*#$;kl<<<aowe**".encodeToByteArray())
            update(salt)
            update("93KKDW2837DCSKLZMNM".encodeToByteArray())
        }.digest()
    }
}