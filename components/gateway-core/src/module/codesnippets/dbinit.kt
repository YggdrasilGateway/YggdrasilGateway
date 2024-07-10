package com.kasukusakura.yggdrasilgateway.core.module.codesnippets

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventPriority
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlConnectionSource
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent

@EventSubscriber
private object Dbinit {
    @EventSubscriber.Handler(priority = EventPriority.HIGHEST)
    private fun init(evt: DatabaseInitializationEvent) {
        mysqlConnectionSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
        CREATE TABLE IF NOT EXISTS codesnippets (
            snippetId varchar(255) not null primary key,
            snippetCode TEXT(25565)
        )
        """.trimIndent()
                )
            }
        }
    }
}