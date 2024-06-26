package com.kasukusakura.yggdrasilgateway.yggdrasil.db

import org.ktorm.schema.Table
import org.ktorm.schema.boolean
import org.ktorm.schema.varchar

internal object PlayerInfoTable : Table<PlayerInfo>("yggdrasil_player_info") {
    val entryId = varchar("entryId").primaryKey().bindTo { it.entryId }
    val declaredYggdrasilTree = varchar("declared_yggdrasil_tree").bindTo { it.declaredYggdrasilTree }

    val upstreamName = varchar("upstream_name").bindTo { it.upstreamName }
    val upstreamNameIgnoreCase = varchar("upstream_name_ci").bindTo { it.upstreamNameIgnoreCase }
    val upstreamUuid = varchar("upstream_uuid").bindTo { it.upstreamUuid }

    val downstreamName = varchar("downstream_name").bindTo { it.downstreamName }
    val downstreamNameIgnoreCase = varchar("downstream_name_ci").bindTo { it.downstreamNameIgnoreCase }
    val downstreamUuid = varchar("downstream_uuid").bindTo { it.downstreamUuid }

    val alwaysPermit = boolean("always_permit").bindTo { it.alwaysPermit }
}