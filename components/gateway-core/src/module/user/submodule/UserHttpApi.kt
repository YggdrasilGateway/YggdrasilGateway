package com.kasukusakura.yggdrasilgateway.core.module.user.submodule

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.http.event.ApiRouteInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessDataResponse
import com.kasukusakura.yggdrasilgateway.core.module.user.principal.GatewayPrincipal
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@EventSubscriber
private object UserHttpApi {
    @EventSubscriber.Handler
    fun ApiRouteInitializeEvent.handle() = route.apply {
        if (!authorization) return@apply

        get("/whoami") {
            call.respond(
                ApiSuccessDataResponse {
                    val principal = call.principal<GatewayPrincipal>()!!
                    "displayName" value principal.displayName
                }
            )
        }
        get("/permission-check/{permission}") {
            val perm = call.parameters["permission"].orEmpty()

            call.respond(
                ApiSuccessDataResponse {
                    "permission" value perm
                    "result" value call.principal<GatewayPrincipal>()!!.hasPermission(perm)
                }
            )
        }
    }
}