package com.kasukusakura.yggdrasilgateway.core.module.user.submodule

import com.auth0.jwt.JWT
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.util.decodeHex
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.core.http.event.ApiRouteInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiRejectedException
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessDataResponse
import com.kasukusakura.yggdrasilgateway.core.module.user.db.UsersTable
import com.kasukusakura.yggdrasilgateway.core.module.user.entry.UserEntryManager
import com.kasukusakura.yggdrasilgateway.core.module.user.principal.GatewayPrincipal
import com.kasukusakura.yggdrasilgateway.core.module.user.principal.UserPrincipal
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.ktorm.dsl.*
import java.time.Instant
import java.util.*
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

            val authed = BasicAuthorization.auth(req.username, pwd, true)
                ?: throw ApiRejectedException("Invalid username/password")

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
        post("/user/reset-password") {
            @Serializable
            data class Req(
                val old: String,
                val new: String,
            )

            val principal = call.principal<GatewayPrincipal>()!!
            if (principal !is UserPrincipal) {
                throw ApiRejectedException("Requiring a user login principal")
            }

            val req = call.receive<Req>()
            withContext(Dispatchers.IO) {
                mysqlDatabase.useTransaction { trans ->
                    trans.connection.autoCommit = false

                    val result = mysqlDatabase.from(UsersTable)
                        .select(UsersTable.password, UsersTable.passwordSalt)
                        .where { (UsersTable.userid eq principal.userid) }
                        .rowSet

                    if (!result.next()) {
                        throw ApiRejectedException("Account not available in database")
                    }
                    val salt = result[UsersTable.passwordSalt]
                        ?: throw ApiRejectedException("This account is a service account")

                    if (!PasswordHasher.hashPassword(req.old.decodeHex(), salt, passwordHashed = true)
                            .contentEquals(result[UsersTable.password])
                    ) {
                        throw ApiRejectedException("reset-password.old-password-incorrect")
                    }

                    mysqlDatabase.update(UsersTable) {
                        where { (UsersTable.userid eq principal.userid) }

                        val newSalt = UUID.randomUUID().toString().toByteArray()
                        set(UsersTable.passwordSalt, newSalt)
                        set(UsersTable.reactiveTime, System.currentTimeMillis() + 1000L)
                        set(
                            UsersTable.password,
                            PasswordHasher.hashPassword(req.new.decodeHex(), newSalt, passwordHashed = true)
                        )
                    }
                }
                UserEntryManager.users.invalidate(principal.userid)
            }

            call.respond(ApiSuccessDataResponse())
        }

    }
}