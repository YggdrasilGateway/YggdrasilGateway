package com.kasukusakura.yggdrasilgateway.core.module.user.submodule

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventPriority
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlConnectionSource
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent

@EventSubscriber
private object DatabaseInitializer {
    @EventSubscriber.Handler(priority = EventPriority.HIGHEST)
    fun DatabaseInitializationEvent.onDatabaseInitializationEvent() {
        mysqlConnectionSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
create table if not exists users
(
    userid       int auto_increment,
    username     varchar(256)      null,
    email        varchar(256)      null,
    password     BLOB              null,
    passwordSalt BLOB              null,
    active       bool default TRUE not null,
    reactiveTime INT8 default 0 not null,
    creationTime INT8 default UNIX_TIMESTAMP() not null,

    constraint users_pk     primary key (userid),
    constraint user_name_pk unique  key (username)
);

                """.trimIndent()
                )
            }
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
create table if not exists user_role (
    userid int not null,
    `role` varchar(256) not null,
    constraint user_role_pk primary key (userid, `role`)
);

                """.trimIndent()
                )
            }

            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
create table if not exists roles (
    `role` varchar(256) not null,
    `desc` varchar(256) null,
    constraint roles_pk primary key (`role`)
);

                """.trimIndent()
                )
            }
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
create table if not exists role_permissions (
    `role` varchar(256) not null,
    `permission` varchar(256) not null,
    constraint role_permissions_pk primary key (`role`, `permission`)
);

                """.trimIndent()
                )
            }
        }
    }
}