package com.kasukusakura.yggdrasilgateway.frontend

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventPriority
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.events.system.YggdrasilHttpServerInitializeEvent
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import java.net.URI

@EventSubscriber
internal object FrontendUI {
    private val httpClient = HttpClient(Java)

    private fun Headers.dropProxiedHeaders(): Headers {
        val thiz = this
        return Headers.build {
            thiz.forEach { key, value ->
                if (HttpHeaders.ContentType.equals(key, ignoreCase = true)) {
                    return@forEach
                }
                if (HttpHeaders.ContentLength.equals(key, ignoreCase = true)) {
                    return@forEach
                }
                appendAll(key, value)
            }
        }
    }

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
                    val contentType = resp.contentType()
                    val contentLength = resp.contentLength()

                    call.respond(object : OutgoingContent.NoContent() {
                        override val contentLength: Long? = contentLength
                        override val contentType: ContentType? get() = contentType
                        override val headers: Headers get() = resp.headers.dropProxiedHeaders()
                        override val status: HttpStatusCode get() = resp.status
                    })
                }

                get("{...}") {
                    val resp = httpClient.get(Url(URI.create(FrontendProperties.devUrl).resolve(call.request.uri)))
                    val contentType = resp.contentType()
                    val contentLength = resp.contentLength()

                    call.respond(object : OutgoingContent.WriteChannelContent() {
                        override val contentLength: Long? = contentLength
                        override val contentType: ContentType? get() = contentType
                        override val headers: Headers get() = resp.headers.dropProxiedHeaders()
                        override val status: HttpStatusCode get() = resp.status

                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            resp.bodyAsChannel().copyAndClose(channel)
                        }
                    })
                }
            }

        }
    }
}