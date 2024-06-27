package com.kasukusakura.yggdrasilgateway.core.http.plugin

import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiFailedResponse
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiRejectedException
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessDataResponse
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessResponse
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

@KtorDsl
public class ApiRouteConfig {}

private val jsonContentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)
private val gson = GsonBuilder().setPrettyPrinting().create()
private val kotlinSerializers = object : ClassValue<KSerializer<*>?>() {
    override fun computeValue(type: Class<*>): KSerializer<*>? {
        return kotlin.runCatching { serializer(type) }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(type: Class<T>): KSerializer<T>? {
        return get(type) as KSerializer<T>?
    }
}

public val ApiRouteInterceptors: RouteScopedPlugin<ApiRouteConfig> = createRouteScopedPlugin(
    "ApiRouteInterceptors",
    ::ApiRouteConfig
) {
    on(CallFailed) { call, error ->
        when (error) {
            is ApiRejectedException -> call.respond(error.toResponse())
            else -> {
                call.application.log.error(
                    "Exception in request {} {}, call {}",
                    call.request.httpMethod,
                    call.request.uri,
                    call,
                    error
                )
                call.respond(
                    ApiFailedResponse(
                        message = error.toString(),
                        errorCode = 500,
                    )
                )
            }
        }
    }
    onCallReceive { call ->
        transformBody { content ->
            val target = requestedType?.type?.java ?: JsonObject::class.java
            kotlinSerializers[target]?.let { ktSerializer ->
                return@transformBody withContext(Dispatchers.IO) {
                    content.toInputStream().bufferedReader(charset = call.request.contentCharset() ?: Charsets.UTF_8)
                        .use { reader ->
                            Json.decodeFromString(ktSerializer, reader.readText())!!
                        }
                }
            }

            val result = withContext(Dispatchers.IO) {
                content.toInputStream().bufferedReader(charset = call.request.contentCharset() ?: Charsets.UTF_8)
                    .use { reader ->
                        gson.fromJson(reader, target)
                    }
            }

            if (JsonPrimitive::class.java.isAssignableFrom(target)) {
                if (target.isInstance(result)) {
                    throw ApiRejectedException(
                        "Excepted a " + target + " but found " + result.javaClass,
                        HttpStatusCode.BadRequest.value
                    )
                }
            }
            if (result == null) {
                throw IllegalStateException("Failed to deserialize to $target")
            }

            return@transformBody result
        }
    }

    onCallRespond { call ->
//        if (call.response.status() == HttpStatusCode.Unauthorized) {
//            if (call.response.sub)
//        }
        transformBody { data ->
            if (data is ApiRejectedException) data.toResponse() else data
        }

        transformBody { data ->
            when (data) {
                is UnauthorizedResponse -> WriterContent(
                    contentType = jsonContentType,
                    status = HttpStatusCode.Unauthorized,
                    body = {
                        gson.newJsonWriter(this).apply {
                            beginObject()

                            name("message").value("Unauthorized")
                            name("errorCode").value(401)

                            endObject().flush()
                        }
                    },
                )
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
