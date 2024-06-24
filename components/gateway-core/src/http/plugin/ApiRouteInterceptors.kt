package com.kasukusakura.yggdrasilgateway.core.http.plugin

import com.google.gson.GsonBuilder
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessDataResponse
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessResponse
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiFailedResponse
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.util.*

@KtorDsl
public class ApiRouteConfig {}

private val jsonContentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)
private val gson = GsonBuilder().setPrettyPrinting().create()

public val ApiRouteInterceptors: RouteScopedPlugin<ApiRouteConfig> = createRouteScopedPlugin(
    "ApiRouteInterceptors",
    ::ApiRouteConfig
) {

    onCallRespond { call ->
        transformBody { data ->
            when (data) {
                is ApiFailedResponse -> {
                    WriterContent(
                        contentType = jsonContentType,
                        status = HttpStatusCode.ExpectationFailed,
                        body = {
                            gson.newJsonWriter(this).apply {
                                beginObject()

                                name("message").value(data.message)
                                name("errorCode").value(data.errorCode)

                                endObject().flush()
                            }
                        },
                    )
                }
                is ApiSuccessDataResponse -> {
                    WriterContent(
                        contentType = jsonContentType,
                        status = HttpStatusCode.OK,
                        body = {
                            gson.newJsonWriter(this).apply {
                                beginObject()

                                name("data")
                                gson.toJson(data.response, this@apply)

                                endObject().flush()
                            }
                        },
                    )
                }
                is ApiSuccessResponse -> {
                    WriterContent(
                        contentType = jsonContentType,
                        status = HttpStatusCode.OK,
                        body = {
                            gson.newJsonWriter(this).apply {
                                beginObject()

                                name("data")
                                gson.toJson(data, data.javaClass, this@apply)

                                endObject().flush()
                            }
                        },
                    )
                }
                else -> data
            }

        }
    }
}
