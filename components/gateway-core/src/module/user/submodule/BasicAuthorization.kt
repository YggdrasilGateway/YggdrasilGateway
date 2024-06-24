package com.kasukusakura.yggdrasilgateway.core.module.user.submodule

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventPriority
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.events.system.YggdrasilHttpServerInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.core.module.user.db.UsersTable
import com.kasukusakura.yggdrasilgateway.core.module.user.principal.UseridPrincipal
import io.ktor.server.auth.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ktorm.dsl.*

@EventSubscriber
private object BasicAuthorization {
    @EventSubscriber.Handler(priority = EventPriority.HIGHER)
    fun YggdrasilHttpServerInitializeEvent.ModuleInitializeEvent.handle() {
        app.authentication {
            basic("basic") {
                validate { cred ->
                    withContext(Dispatchers.IO) verify@{
                        val saltQuery = mysqlDatabase.from(UsersTable)
                            .select(UsersTable.passwordSalt, UsersTable.password, UsersTable.userid)
                            .where { UsersTable.username eq cred.name and UsersTable.active eq true }
                            .rowSet
                        if (!saltQuery.next()) {
                            return@verify null
                        }
                        val salt = saltQuery[UsersTable.passwordSalt] ?: return@verify null

                        if (!PasswordHasher.hashPassword(cred.password.toByteArray(), salt)
                                .contentEquals(saltQuery[UsersTable.password])
                        ) {
                            return@verify null
                        }

                        UseridPrincipal(saltQuery[UsersTable.userid] ?: error("Failed to get userid"))
                    }
                }
            }

        }
    }
}