package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys

import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.provider.YggdrasilServiceWithTimeout
import com.kasukusakura.yggdrasilgateway.yggdrasil.remote.YggdrasilServiceProviders

internal class LoadedYggdrasilService(
    val id: String,
    val urlPath: String,
    val comment: String?,
    val active: Boolean,
    val connectionTimeout: Long,
    val limited: Boolean,
) {
    val service by lazy {
        if (connectionTimeout == 0L) {
            YggdrasilServiceProviders.constructService(urlPath)
        } else YggdrasilServiceWithTimeout(
            timeout = connectionTimeout,
            delegate = YggdrasilServiceProviders.constructService(urlPath),
        )
    }
}