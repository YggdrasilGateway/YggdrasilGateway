package com.kasukusakura.yggdrasilgateway.core.module.message.db

import org.ktorm.entity.Entity
import org.ktorm.schema.Table
import org.ktorm.schema.varchar

internal object MessagesTable : Table<MessagesTable.Message>("messages") {
    interface Message : Entity<Message> {
        companion object : Entity.Factory<Message>()

        var key: String
        var value: String
    }

    val key = varchar("key").primaryKey().bindTo { it.key }
    val value = varchar("value").bindTo { it.value }
}