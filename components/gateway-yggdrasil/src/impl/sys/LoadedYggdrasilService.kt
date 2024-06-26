package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys

import com.kasukusakura.yggdrasilgateway.yggdrasil.remote.YggdrasilServiceProviders

internal class LoadedYggdrasilService(
    val id: String,
    val urlPath: String,
    val comment: String?,
    val active: Boolean,
) {
    val service by lazy {
        YggdrasilServiceProviders.constructService(urlPath)
    }
}