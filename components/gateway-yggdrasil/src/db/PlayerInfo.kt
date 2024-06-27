package com.kasukusakura.yggdrasilgateway.yggdrasil.db

import org.ktorm.entity.Entity

internal interface PlayerInfo : Entity<PlayerInfo> {
    var entryId: String
    var declaredYggdrasilTree: String

    var upstreamName: String
    val upstreamNameIgnoreCase: String
    var upstreamUuid: String

    var downstreamName: String
    val downstreamNameIgnoreCase: String
    var downstreamUuid: String

    var alwaysPermit: Boolean
    val indexer: Long

    companion object : Entity.Factory<PlayerInfo>()
}