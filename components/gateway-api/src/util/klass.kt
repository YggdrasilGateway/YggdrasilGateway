package com.kasukusakura.yggdrasilgateway.api.util

public fun <T : Any> Class<T>.loadInstance(): T {
    return runCatching {
        this@loadInstance.kotlin.objectInstance
    }.orElse {
        val field = getDeclaredField("INSTANCE")
        field.isAccessible = true
        cast(field.get(null))
    }.orElse {
        newInstance()
    }.getOrThrow()
}