package com.kasukusakura.yggdrasilgateway.core.module.user.submodule

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventPriority
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.events.system.YggdrasilHttpServerInitializeEvent
import com.kasukusakura.yggdrasilgateway.api.properties.SimpleProperties
import com.kasukusakura.yggdrasilgateway.core.module.user.entry.UserEntryManager
import com.kasukusakura.yggdrasilgateway.core.module.user.principal.JWTPrincipal
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.io.File
import java.util.*

@EventSubscriber
internal object JwtAuthorizationConfig : SimpleProperties("jwt", file = File("data/jwt.properties")) {
    var secret = UUID.randomUUID().toString() + UUID.randomUUID().toString()
    var issuer = "yggdrasil-gateway-" + UUID.randomUUID()
    var relam = issuer

    @field:Transient
    lateinit var algorithm: Algorithm
        private set

    override fun reload() {
        super.reload()
        algorithm = Algorithm.HMAC512(secret)
    }
}

@EventSubscriber
internal object JwtAuthorization {
    @EventSubscriber.Handler(priority = EventPriority.HIGHER)
    fun YggdrasilHttpServerInitializeEvent.ModuleInitializeEvent.handle() {

        val jwtVerifier = JWT
            .require(JwtAuthorizationConfig.algorithm)
            .withIssuer(JwtAuthorizationConfig.issuer)
            .build()!!

        app.authentication {
            jwt("jwt") {
                verifier(jwtVerifier)
                realm = JwtAuthorizationConfig.relam
                authSchemes("jwt", "Bearer", "token")
                validate { cred ->
                    val notBeforeAsInstant = cred.payload.notBeforeAsInstant ?: return@validate null

                    if (cred.audience.any { it == "authorization/user" }) {
                        val sub = cred.payload.subject?.toIntOrNull() ?: return@validate null

                        return@validate UserEntryManager.users[sub]
                            ?.takeIf { it.active && notBeforeAsInstant.toEpochMilli() > it.reactiveTime }
                            ?.let { JWTPrincipal.User(cred, it) }
                    }

                    return@validate null
                }
            }
        }
    }
}