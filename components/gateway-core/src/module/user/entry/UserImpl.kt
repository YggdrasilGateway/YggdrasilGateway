package com.kasukusakura.yggdrasilgateway.core.module.user.entry

import com.kasukusakura.yggdrasilgateway.core.module.user.principal.GatewayPrincipal

internal class UserImpl(
    val userid: Int,
    var username: String,
    var email: String?,
    var active: Boolean,
    var roles: Set<String>,
) : GatewayPrincipal {
    override val displayName: String
        get() = username

    override fun hasPermission(permission: String): Boolean {
        roles.forEach { role ->
            val roleImpl = UserEntryManager.roles[role] ?: return@forEach
            if (roleImpl.hasPermission(permission)) return true
        }
        return false
    }
}