package com.kasukusakura.yggdrasilgateway.core.module.user.principal

import com.google.gson.JsonObject
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonArray
import io.ktor.server.auth.jwt.*

public abstract class JWTPrincipal : GatewayPrincipal {
    public abstract val cred: JWTCredential
    public abstract val delegate: GatewayPrincipal

    override fun reportInformation(output: JsonObject) {
        output.addProperty("jwt.createTime", cred.payload.notBeforeAsInstant?.toEpochMilli())
        output.addProperty("jwt.expireTime", cred.payload.expiresAtAsInstant?.toEpochMilli())
        output.add("jwt.expireTime", cred.payload.audience?.let { aud ->
            buildJsonArray { aud.forEach { add(it) } }
        })
        delegate.reportInformation(output)
    }

    public class Basic(
        override val cred: JWTCredential,
        override val delegate: GatewayPrincipal,
    ) : JWTPrincipal(), GatewayPrincipal by delegate {
        override fun reportInformation(output: JsonObject) {
            super<JWTPrincipal>.reportInformation(output)
        }
    }

    public class User(
        override val cred: JWTCredential,
        override val delegate: UserPrincipal,
    ) : JWTPrincipal(), UserPrincipal by delegate {
        override fun reportInformation(output: JsonObject) {
            super<JWTPrincipal>.reportInformation(output)
        }
    }
}