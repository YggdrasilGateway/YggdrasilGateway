package com.kasukusakura.yggdrasilgateway.core.module.user.principal

import com.kasukusakura.yggdrasilgateway.core.module.user.entry.UserEntryManager

public class UseridPrincipal(override val userid: Int) : UserPrincipal {
    private val realUser by lazy { UserEntryManager.users[userid] }

    override val displayName: String get() = realUser?.displayName ?: userid.toString()
    override val username: String
        get() = realUser?.username ?: ""

    override fun hasPermission(permission: String): Boolean {
        return realUser?.hasPermission(permission) ?: false
    }
}