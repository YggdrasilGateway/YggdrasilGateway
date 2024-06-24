package com.kasukusakura.yggdrasilgateway.core.module.user

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.events.system.YggdrasilHttpServerInitializeEvent
import com.kasukusakura.yggdrasilgateway.api.util.eventFire
import com.kasukusakura.yggdrasilgateway.core.http.event.ApiRouteInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.http.plugin.ApiRouteInterceptors
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiFailedResponse
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking

@EventSubscriber
internal object UserModule {
    @EventSubscriber.Handler
    fun YggdrasilHttpServerInitializeEvent.ModuleInitializeEvent.handle() {
        app.routing {
            val apiRoute = createRouteFromPath("/api")
            apiRoute.install(ApiRouteInterceptors)

            runBlocking { ApiRouteInitializeEvent(apiRoute, false).eventFire() }

            apiRoute.authenticate("basic") {
                runBlocking { ApiRouteInitializeEvent(this@authenticate, true).eventFire() }
            }


            apiRoute.route("{...}") {
                handle {
                    call.respond(
                        ApiFailedResponse(
                            message = "No endpoint found.",
                            errorCode = 404,
                        )
                    )
                }
            }
        }
    }
}