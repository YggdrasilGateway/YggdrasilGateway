package com.kasukusakura.yggdrasilgateway.core.module.message.submodule

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventPriority
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlConnectionSource
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent
import com.kasukusakura.yggdrasilgateway.core.module.message.db.MessagesTable
import org.ktorm.entity.forEach
import org.ktorm.entity.sequenceOf
import java.util.concurrent.ConcurrentHashMap

@EventSubscriber
internal object DbAccess {
    val loadedTexts = ConcurrentHashMap<String, String>()

    @EventSubscriber.Handler(priority = EventPriority.HIGHEST)
    fun DatabaseInitializationEvent.onDatabaseInitializationEvent() {
        mysqlConnectionSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
create table if not exists messages
(
    `key`           varchar(256)      not null,
    `value`         varchar(256)      null,

    constraint msg_pk     primary key (`key`)
);

                """.trimIndent()
                )
            }
        }

        mysqlDatabase.sequenceOf(MessagesTable)
            .forEach { loadedTexts[it.key] = it.value }
    }
}