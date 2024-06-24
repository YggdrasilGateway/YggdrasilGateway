package com.kasukusakura.yggdrasilgateway.core.module.user.principal

import io.ktor.server.auth.*

public interface GatewayPrincipal : Principal {
    public val displayName: String
    public fun hasPermission(permission: String): Boolean
}