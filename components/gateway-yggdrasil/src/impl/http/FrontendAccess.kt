package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.http

import com.google.gson.JsonObject
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonArray
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonObject
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent
import com.kasukusakura.yggdrasilgateway.core.http.event.ApiRouteInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiRejectedException
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessDataResponse
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.YggdrasilServicesTable
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.YggdrasilServicesHolder
import com.kasukusakura.yggdrasilgateway.yggdrasil.remote.YggdrasilServiceProviders
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.insert
import org.ktorm.dsl.update
import org.slf4j.LoggerFactory
import java.util.*

@EventSubscriber
internal object FrontendAccess {
    private val log = LoggerFactory.getLogger(ApiServer::class.java)
    private val REJECTION_UUID = UUID(0, 0)

    @EventSubscriber.Handler
    fun ApiRouteInitializeEvent.handle() {
        if (!authorization) return
        route.createRouteFromPath("/yggdrasil-cgi").mount()
    }

    private fun Route.mount() {
        get("/services") {
            call.respond(ApiSuccessDataResponse(buildJsonArray {
                YggdrasilServicesHolder.services.values.forEach { service ->
                    +buildJsonObject {
                        "id"(service.id)
                        "urlPath"(service.urlPath)
                        "active"(service.active)
                        "comment"(service.comment)
                    }
                }
            }))
        }
        patch("/services") {
            val conf = call.receive<JsonObject>()
            val id = conf.getAsJsonPrimitive("id")?.asString ?: throw ApiRejectedException("Missing id")
            val urlPath = conf.getAsJsonPrimitive("urlPath")?.asString ?: throw ApiRejectedException("Missing urlPath")
            val comment = conf.getAsJsonPrimitive("comment")?.asString
            val active = conf.getAsJsonPrimitive("active")?.asBoolean ?: true

            kotlin.runCatching {
                YggdrasilServiceProviders.constructService(urlPath)
            }.onFailure { err ->
                throw ApiRejectedException("No service available for $urlPath -> $err")
            }

            val targetService = YggdrasilServicesHolder.services[id]
            if (targetService == null) {
                mysqlDatabase.insert(YggdrasilServicesTable) {
                    set(YggdrasilServicesTable.id, id)
                    set(YggdrasilServicesTable.urlPath, urlPath)
                    set(YggdrasilServicesTable.comment, comment)
                    set(YggdrasilServicesTable.active, active)
                }
            } else {
                mysqlDatabase.update(YggdrasilServicesTable) {
                    where { it.id eq id }
                    set(YggdrasilServicesTable.urlPath, urlPath)
                    set(YggdrasilServicesTable.comment, comment)
                    set(YggdrasilServicesTable.active, active)
                }
            }
            YggdrasilServicesHolder.reloadServices(DatabaseInitializationEvent)

            call.respond(ApiSuccessDataResponse())
        }
        delete("/services/{id}") {
            val id = call.parameters["id"] ?: throw ApiRejectedException("Missing service id")
            YggdrasilServicesHolder.services[id] ?: throw ApiRejectedException("Service $id not found")
            mysqlDatabase.delete(YggdrasilServicesTable) { it.id eq id }
            YggdrasilServicesHolder.reloadServices(DatabaseInitializationEvent)
            call.respond(ApiSuccessDataResponse())
        }
    }
}