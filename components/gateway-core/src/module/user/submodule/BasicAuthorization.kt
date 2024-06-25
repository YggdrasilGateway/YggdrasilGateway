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
internal object BasicAuthorization {
    fun auth(username: String, password: ByteArray, hashed: Boolean): UseridPrincipal? {
        val saltQuery = mysqlDatabase.from(UsersTable)
            .select(UsersTable.passwordSalt, UsersTable.password, UsersTable.userid)
            .where { (UsersTable.username eq username) and (UsersTable.active eq true) }
            .rowSet
        if (!saltQuery.next()) {
            return null
        }
        val salt = saltQuery[UsersTable.passwordSalt] ?: return null

        if (!PasswordHasher.hashPassword(password, salt, passwordHashed = hashed)
                .contentEquals(saltQuery[UsersTable.password])
        ) {
            return null
        }

        return UseridPrincipal(saltQuery[UsersTable.userid] ?: error("Failed to get userid"))
    }

    @EventSubscriber.Handler(priority = EventPriority.HIGHER)
    fun YggdrasilHttpServerInitializeEvent.ModuleInitializeEvent.handle() {
        app.authentication {
            basic("basic") {
                validate { cred ->
                    withContext(Dispatchers.IO) {
                        auth(cred.name, cred.password.toByteArray(), false)
                    }
                }
            }

        }
    }
}