package com.kasukusakura.yggdrasilgateway.core.module.user.entry

import com.kasukusakura.yggdrasilgateway.core.module.user.principal.GatewayPrincipal

internal open class RoleImpl(
    val roleName: String,
    var desc: String?,
    var grantedPermissions: Set<String>,
) : GatewayPrincipal {
    override val displayName: String get() = roleName

    override fun hasPermission(permission: String): Boolean {
        if (permission in grantedPermissions) return true

        val splitRegions = permission.split('.')
        for (i in splitRegions.size - 1 downTo 0) {
            val wildcardPerm = splitRegions.asSequence().take(i).plus("*").joinToString(".")
            if (wildcardPerm in grantedPermissions) return true
        }
        return false
    }
}