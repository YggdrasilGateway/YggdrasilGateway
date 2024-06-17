package com.kasukusakura.yggdrasilgateway.frontend

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventPriority
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.events.system.YggdrasilHttpServerInitializeEvent
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URI

@EventSubscriber
internal object FrontendUI {
    private val httpClient = HttpClient(Java)

    @EventSubscriber.Handler(priority = EventPriority.LOWEST)
    fun YggdrasilHttpServerInitializeEvent.ModuleInitializeEvent.handle() {
        if (FrontendProperties.devUrl.isNotEmpty()) {

            app.routing {
                get("/hi") {
                    call.respond("Pong!")
                }
            }

            app.routing {
                head("{...}") {
                    val resp = httpClient.head(Url(URI.create(FrontendProperties.devUrl).resolve(call.request.uri)))
                    call.response.status(resp.status)
                    resp.headers.forEach { s, strings ->
                        strings.forEach { v ->
                            call.response.headers.append(s, v)
                        }
                    }
                    call.respond(resp.bodyAsChannel())
                }

                get("{...}") {
                    val resp = httpClient.get(Url(URI.create(FrontendProperties.devUrl).resolve(call.request.uri)))
                    call.response.status(resp.status)
                    resp.headers.forEach { s, strings ->
                        strings.forEach { v ->
                            call.response.headers.append(s, v)
                        }
                    }

                    call.respond(resp.bodyAsChannel())
                }
            }

        }
    }
}