package com.kasukusakura.yggdrasilgateway.core.module.user.submodule

import com.auth0.jwt.JWT
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.util.decodeHex
import com.kasukusakura.yggdrasilgateway.core.http.event.ApiRouteInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiRejectedException
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessDataResponse
import com.kasukusakura.yggdrasilgateway.core.module.user.principal.GatewayPrincipal
import com.kasukusakura.yggdrasilgateway.core.module.user.principal.UserPrincipal
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.TimeUnit

@EventSubscriber
private object UserHttpApi {
    @EventSubscriber.Handler
    fun ApiRouteInitializeEvent.handleLogin() = route.apply {
        if (authorization) return@apply

        post("/user/login") {
            @Serializable
            data class Req(
                val username: String,
                val password: String,
            )
            val req = call.receive<Req>()
            val pwd = req.password.decodeHex()

            val authed = BasicAuthorization.auth(req.username, pwd, true) ?: throw ApiRejectedException("Invalid username/password")

            val now = System.currentTimeMillis()
            val jwt = JWT.create()
                .withIssuer(JwtAuthorizationConfig.issuer)
                .withAudience("authorization/user")
                .withSubject(authed.userid.toString())
                .withNotBefore(Instant.ofEpochMilli(now))
                .withExpiresAt(Instant.ofEpochMilli(now + 1000L * 60))
                .sign(JwtAuthorizationConfig.algorithm)

            call.respond(ApiSuccessDataResponse {
                "token" value jwt
            })
        }
    }

    @EventSubscriber.Handler
    fun ApiRouteInitializeEvent.handle() = route.apply {
        if (!authorization) return@apply

        get("/user/whoami") {
            call.respond(
                ApiSuccessDataResponse {
                    val principal = call.principal<GatewayPrincipal>()!!
                    "displayName" value principal.displayName
                    principal.reportInformation(this.delegate)
                }
            )
        }
        get("/user/permission-check/{permission}") {
            val perm = call.parameters["permission"].orEmpty()

            call.respond(
                ApiSuccessDataResponse {
                    "permission" value perm
                    "result" value call.principal<GatewayPrincipal>()!!.hasPermission(perm)
                }
            )
        }
        post("/user/refresh-jwt") {
            val principal = call.principal<GatewayPrincipal>()!!
            if (principal !is UserPrincipal) {
                throw ApiRejectedException("Requiring a user login principal")
            }

            @Serializable
            data class Req(
                val time: Long = TimeUnit.MINUTES.toMillis(5),
            )

            val req = call.receive<Req>()

            val now = System.currentTimeMillis()
            val jwt = JWT.create()
                .withIssuer(JwtAuthorizationConfig.issuer)
                .withAudience("authorization/user")
                .withSubject(principal.userid.toString())
                .withNotBefore(Instant.ofEpochMilli(now))
                .withExpiresAt(Instant.ofEpochMilli(now + req.time))
                .sign(JwtAuthorizationConfig.algorithm)

            call.respond(ApiSuccessDataResponse {
                "token" value jwt
                "validToInstant" value Instant.ofEpochMilli(now + req.time).toString()
                "validToTimestamp" value (now + req.time)
            })
        }

    }
}