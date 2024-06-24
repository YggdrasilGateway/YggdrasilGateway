package com.kasukusakura.yggdrasilgateway.core.module.user.principal

public interface UserPrincipal : GatewayPrincipal {
    public val userid: Int
    public val username: String
}