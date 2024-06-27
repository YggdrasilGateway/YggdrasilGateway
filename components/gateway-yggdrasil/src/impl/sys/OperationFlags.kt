package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys

import io.ktor.util.*
import java.security.KeyPairGenerator

internal class OperationFlags {
    var processMode = ProcessMode.COMPLETE_TEST
    var autoResolveName = true
    var autoResolveUuidConflict = true
    var enchantedErrorRejection = true
    var prohibitMode = false

    var deliveredPublicKey = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.generateKeyPair().let { kp ->
        buildString {
            append("-----BEGIN PUBLIC KEY-----")
            append(kp.public.encoded.encodeBase64())
            append("-----END PUBLIC KEY-----")
        }
    }

    enum class ProcessMode {
        FIRST_SUCCESS,
        ERROR_SKIP,
        COMPLETE_TEST,
    }
}