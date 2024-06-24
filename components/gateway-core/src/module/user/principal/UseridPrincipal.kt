package com.kasukusakura.yggdrasilgateway.core.module.user.principal

import com.kasukusakura.yggdrasilgateway.core.module.user.entry.UserEntryManager

public class UseridPrincipal(public val userid: Int) : GatewayPrincipal {
    private val realUser by lazy { UserEntryManager.users[userid] }

    override val displayName: String get() = realUser?.displayName ?: userid.toString()

    override fun hasPermission(permission: String): Boolean {
        return realUser?.hasPermission(permission) ?: false
    }
}