package com.kasukusakura.yggdrasilgateway.core.module.user.submodule

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventPriority
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent
import com.kasukusakura.yggdrasilgateway.core.module.user.db.UserRoleTable
import com.kasukusakura.yggdrasilgateway.core.module.user.db.UsersTable
import org.ktorm.dsl.*
import org.slf4j.LoggerFactory

@EventSubscriber
internal object FirstUserInitializer {
    private val log = LoggerFactory.getLogger(FirstUserInitializer::class.java)

    @EventSubscriber.Handler(priority = EventPriority.HIGH)
    fun DatabaseInitializationEvent.handle() {
        mysqlDatabase.useTransaction { trans ->
            trans.connection.autoCommit = false

            val result = mysqlDatabase.from(UsersTable)
                .select(count())
                .rowSet
            result.next()
            if (result.getInt(1) == 0) {
                log.info("Creating First User...")

                mysqlDatabase.insert(UsersTable) {
                    set(UsersTable.username, "admin")
                    set(UsersTable.email, "admin@localhost")
                    set(UsersTable.password, PasswordHasher.hashPassword("admin".toByteArray(), "admin".toByteArray()))
                    set(UsersTable.passwordSalt, "admin".toByteArray())
                }

                val uid = mysqlDatabase.from(UsersTable)
                    .select(UsersTable.userid)
                    .where { UsersTable.username eq "admin" }
                    .rowSet.also { it.next() }[UsersTable.userid]


                mysqlDatabase.insert(UserRoleTable) {
                    set(UserRoleTable.userid, uid)
                    set(UserRoleTable.role, "yg-admin")
                }
            }
        }
    }
}