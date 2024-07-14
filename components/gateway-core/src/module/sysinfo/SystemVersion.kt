package com.kasukusakura.yggdrasilgateway.core.module.sysinfo

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.http.event.ApiRouteInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessDataResponse
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@EventSubscriber
private object SystemVersion {
    @EventSubscriber.Handler
    fun ApiRouteInitializeEvent.handle() {
        if (!authorization) return
        route.get("/system/version") {
            call.respond(ApiSuccessDataResponse {
                "backend"(SystemVersion::class.java.`package`.implementationVersion ?: "dev")
            })
        }
    }
}