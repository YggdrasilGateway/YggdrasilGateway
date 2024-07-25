package com.kasukusakura.yggdrasilgateway.core.module.user.entry

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent
import com.kasukusakura.yggdrasilgateway.core.module.user.db.RolePermissionsTable
import com.kasukusakura.yggdrasilgateway.core.module.user.db.RoleTable
import com.kasukusakura.yggdrasilgateway.core.module.user.db.UserRoleTable
import com.kasukusakura.yggdrasilgateway.core.module.user.db.UsersTable
import org.ktorm.dsl.*
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

@EventSubscriber
internal object UserEntryManager {
    private val log = LoggerFactory.getLogger(UserEntryManager::class.java)

    val roles = mutableMapOf<String, RoleImpl>()
    val users = CacheBuilder.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .maximumSize(1024)
        .build(object : CacheLoader<Int, UserImpl?>() {
            @Suppress("WRONG_NULLABILITY_FOR_JAVA_OVERRIDE")
            override fun load(key: Int): UserImpl? {
                return loadUserFromDb(key)
            }
        })

    @EventSubscriber.Handler
    private fun loadFromDb(event: DatabaseInitializationEvent) {
        mysqlDatabase.useTransaction { trans ->
            trans.connection.autoCommit = false

            mysqlDatabase.from(RoleTable)
                .select(RoleTable.role, RoleTable.desc)
                .forEach { result ->
                    log.info("Loading rule {}", result[RoleTable.role])
                    val perms = mutableSetOf<String>()
                    val newRole = RoleImpl(
                        roleName = result[RoleTable.role]!!,
                        desc = result[RoleTable.desc],
                        grantedPermissions = perms,
                    )

                    if (newRole.roleName.startsWith("yg-")) {
                        log.info("Skipped loading internal role {} from database", newRole.roleName)
                        return@forEach
                    }

                    roles[newRole.roleName] = newRole

                    mysqlDatabase.from(RolePermissionsTable)
                        .select(RolePermissionsTable.perm)
                        .where { RolePermissionsTable.role eq newRole.roleName }
                        .forEach { perms.add(it[RolePermissionsTable.perm]!!) }
                }
        }
    }

    private fun loadUserFromDb(uid: Int): UserImpl? {
        mysqlDatabase.useTransaction { trans ->
            trans.connection.autoCommit = false

            val userLookup = mysqlDatabase.from(UsersTable)
                .select(UsersTable.email, UsersTable.username, UsersTable.active, UsersTable.reactiveTime)
                .where { UsersTable.userid eq uid }
                .rowSet

            if (!userLookup.next()) return null
            val roles = mutableSetOf<String>()
            val newUser = UserImpl(
                userid = uid,
                username = userLookup[UsersTable.username] ?: uid.toString(),
                active = userLookup[UsersTable.active] ?: false,
                email = userLookup[UsersTable.email],
                roles = roles,
                reactiveTime = userLookup[UsersTable.reactiveTime]!!,
            )

            mysqlDatabase.from(UserRoleTable)
                .select(UserRoleTable.role)
                .where { UserRoleTable.userid eq uid }
                .forEach { roles.add(it[UserRoleTable.role]!!) }

            return newUser
        }
    }

    init {
        roles["yg-admin"] = object : RoleImpl(
            roleName = "yg-admin",
            desc = "Yggdrasil Gateway Admin",
            grantedPermissions = setOf("*"),
        ) {
            override fun hasPermission(permission: String): Boolean = true
        }
    }
}