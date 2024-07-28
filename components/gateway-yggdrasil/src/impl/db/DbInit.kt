package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.db

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventPriority
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlConnectionSource
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent

@EventSubscriber
private object DbInit {
    @EventSubscriber.Handler(priority = EventPriority.HIGHEST)
    fun DatabaseInitializationEvent.handle() {
        mysqlConnectionSource.connection.use { connection ->
            connection.createStatement().use { statement ->

                if (!connection.metaData.getTables(null, null, "yggdrasil_player_info", null).next()) {

                    statement.executeUpdate(
                        """
create table if not exists yggdrasil_player_info
(
    entryId                 varchar(32) collate ascii_bin    not null,
    declared_yggdrasil_tree varchar(32)                      not null,
    upstream_name           varchar(256) collate utf8mb4_bin not null,
    upstream_name_ci        varchar(256) collate utf8mb4_general_ci as (`upstream_name`),
    downstream_name         varchar(256) collate utf8mb4_bin not null,
    downstream_name_ci      varchar(256) collate utf8mb4_general_ci as (`downstream_name`),
    upstream_uuid           varchar(32) collate ascii_bin    not null,
    downstream_uuid         varchar(32) collate ascii_bin    not null,
    always_permit           bool default false               not null,
    indexer                 bigint auto_increment,

    constraint indexer unique (indexer),
    constraint entryId primary key (entryId)
);

                """.trimIndent()
                    )
                    statement.executeUpdate(
                        """
create index downstream_uuid
on yggdrasil_player_info (downstream_uuid);
                    """.trimIndent()
                    )
                    statement.executeUpdate(
                        """
create index upstream_uuid
on yggdrasil_player_info (upstream_uuid);
                    """.trimIndent()
                    )
                    statement.executeUpdate(
                        """
create index downstream_name_ci
on yggdrasil_player_info (downstream_name_ci);
                    """.trimIndent()
                    )
                    statement.executeUpdate(
                        """
create index upstream_name_ci
on yggdrasil_player_info (upstream_name_ci);
                    """.trimIndent()
                    )
                }

                statement.executeUpdate(
                    """
create table if not exists yggdrasil_services
(
    id                      varchar(32) collate ascii_bin    not null,
    urlPath                 varchar(256)                     not null,
    comment                 varchar(256)                     null,
    active                  boolean default true             not null,
    limited                 boolean default false            not null,
    connection_timeout      int8  default 0                  not null,
    constraint entryId
        primary key (id)
);

                """.trimIndent()
                )
                kotlin.runCatching {
                    statement.executeUpdate("""
ALTER TABLE yggdrasil_services ADD COLUMN connection_timeout int8 default 0 not null;
                    """.trimIndent())
                }
                kotlin.runCatching {
                    statement.executeUpdate("""
ALTER TABLE yggdrasil_services ADD COLUMN limited boolean default false not null;
                    """.trimIndent())
                }

                statement.executeUpdate(
                    """
create table if not exists yggdrasil_options
(
    `key`                     varchar(64) collate ascii_bin    not null,
    value                     varchar(512) collate utf8mb4_bin null,
    constraint confKey primary key (`key`)
);

                """.trimIndent()
                )
            }
        }
    }
}