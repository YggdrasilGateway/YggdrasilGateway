package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.provider

import com.kasukusakura.yggdrasilgateway.yggdrasil.data.PlayerProfile
import com.kasukusakura.yggdrasilgateway.yggdrasil.remote.YggdrasilService
import io.ktor.http.*
import kotlinx.coroutines.withTimeout

internal class YggdrasilServiceWithTimeout(
    val delegate: YggdrasilService,
    val timeout: Long,
) : YggdrasilService {
    override suspend fun hasJoined(params: Parameters): PlayerProfile? = withTimeout(timeout) {
        delegate.hasJoined(params)
    }

    override suspend fun queryProfile(uuid: String, unsigned: Boolean): PlayerProfile? = withTimeout(timeout) {
        delegate.queryProfile(uuid, unsigned)
    }

    override suspend fun batchQuery(name: List<String>): List<PlayerProfile> = withTimeout(timeout) {
        delegate.batchQuery(name)
    }
}