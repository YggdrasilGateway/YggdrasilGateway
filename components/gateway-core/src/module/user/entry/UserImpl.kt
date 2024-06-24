package com.kasukusakura.yggdrasilgateway.core.module.user.entry

import com.kasukusakura.yggdrasilgateway.core.module.user.principal.UserPrincipal

internal class UserImpl(
    override val userid: Int,
    override var username: String,
    var email: String?,
    var active: Boolean,
    var roles: Set<String>,
) : UserPrincipal {
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