package com.kasukusakura.yggdrasilgateway.core.module.user.principal

import com.google.gson.JsonObject
import io.ktor.server.auth.*

public interface GatewayPrincipal : Principal {
    public val displayName: String
    public fun hasPermission(permission: String): Boolean

    public fun reportInformation(output: JsonObject) {}
}