package com.kasukusakura.yggdrasilgateway.core.module.message.submodule

import com.google.gson.JsonObject
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonObject
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.core.http.event.ApiRouteInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiRejectedException
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessDataResponse
import com.kasukusakura.yggdrasilgateway.core.module.message.MessagesModule
import com.kasukusakura.yggdrasilgateway.core.module.message.db.MessagesTable
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.support.mysql.insertOrUpdate

@EventSubscriber
private object HttpApi {
    @EventSubscriber.Handler
    fun ApiRouteInitializeEvent.handle() {
        if (!authorization) return

        route.createRouteFromPath("/messages").mount()
    }

    fun Route.mount() {
        get("/default") {
            call.respond(ApiSuccessDataResponse(MessagesModule.defaultsTextSnapshot))
        }
        get("/defined") {
            call.respond(ApiSuccessDataResponse(buildJsonObject {
                DbAccess.loadedTexts.forEach { put(it.key, it.value) }
            }))
        }
        post("/update") {
            val req = call.receive<JsonObject>()
            val key = req.getAsJsonPrimitive("key")?.asString ?: throw ApiRejectedException("Missing key")
            val value = req.getAsJsonPrimitive("value")?.asString?.takeIf { it.isNotEmpty() }

            if (value == null) {
                mysqlDatabase.delete(MessagesTable) { it.key eq key }
                DbAccess.loadedTexts.remove(key)

            } else {
                mysqlDatabase.insertOrUpdate(MessagesTable) {
                    set(it.key, key)
                    set(it.value, value)

                    onDuplicateKey { set(it.value, value) }
                }
                DbAccess.loadedTexts[key] = value
            }
            call.respond(ApiSuccessDataResponse())
        }
    }
}