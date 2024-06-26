package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.evt

import com.kasukusakura.yggdrasilgateway.yggdrasil.data.PlayerProfile
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.PlayerInfo
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.LoadedYggdrasilService

internal class UpstreamPlayerTransformEvent(
    val service: LoadedYggdrasilService,
    val profile: PlayerProfile,
    var result: PlayerProfile,
    var skipRestrictTest: Boolean = false,
) {
    lateinit var targetPlayer: PlayerInfo
    val targetPlayerInitialized get() = ::targetPlayer.isInitialized
}