package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.http

import com.google.gson.JsonObject
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonArray
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonObject
import com.kasukusakura.yggdrasilgateway.api.util.eventFire
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent
import com.kasukusakura.yggdrasilgateway.core.http.event.ApiRouteInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiRejectedException
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessDataResponse
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.PlayerInfoTable
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.YggdrasilServicesTable
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.evt.UpstreamPlayerTransformEvent
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.YggdrasilServicesHolder
import com.kasukusakura.yggdrasilgateway.yggdrasil.remote.YggdrasilServiceProviders
import com.kasukusakura.yggdrasilgateway.yggdrasil.util.parseUuid
import com.kasukusakura.yggdrasilgateway.yggdrasil.util.toStringUnsigned
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.Serializable
import org.ktorm.dsl.*
import org.ktorm.entity.*
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
        /// Services

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

        /// Players

        get("/players") {
            val playerInfoTable = mysqlDatabase.sequenceOf(PlayerInfoTable)
            val indexer = call.request.queryParameters["index"]?.toIntOrNull() ?: 0
            val search = call.request.queryParameters["search"]

            val total: Int
            val sqlTerm = if (search == null) {
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20


                playerInfoTable
                    .sortedBy { it.indexer }
                    .also { total = it.count() }
                    .drop(indexer)
                    .take(pageSize)
            } else {
                val uuid = kotlin.runCatching { search.parseUuid().toStringUnsigned() }.getOrElse { search }

                playerInfoTable.filter {
                    (it.upstreamNameIgnoreCase eq search)
                        .or(it.downstreamNameIgnoreCase eq search)
                        .or(it.upstreamUuid eq uuid)
                        .or(it.downstreamUuid eq uuid)
                }.also { total = it.count() }
            }

            call.respond(ApiSuccessDataResponse {
                "total"(total)
                "values" arr {
                    sqlTerm.forEach { entity ->
                        +buildJsonObject {
                            "entryId"(entity.entryId)
                            "upstreamName"(entity.upstreamName)
                            "downstreamName"(entity.downstreamName)
                            "upstreamUuid"(entity.upstreamUuid)
                            "downstreamUuid"(entity.downstreamUuid)
                            "declared"(entity.declaredYggdrasilTree)
                            "alwaysPermit"(entity.alwaysPermit)
                        }
                    }
                }
            })

        }
        patch("/players") {
            val conf = call.receive<JsonObject>()
            val entryId = conf.getAsJsonPrimitive("entryId")?.asString ?: throw ApiRejectedException("Missing entry id")

            val downstreamUuid = conf.getAsJsonPrimitive("downstreamUuid")?.asString
                ?.takeIf { it.isNotBlank() }
                ?.let { result ->
                    kotlin.runCatching { result.parseUuid() }
                        .getOrElse { throw ApiRejectedException("Invalid downstream uuid") }
                }

            val downstreamName = conf.getAsJsonPrimitive("downstreamName")?.asString
                ?.takeIf { it.isNotBlank() }

            val alwaysPermit = conf.getAsJsonPrimitive("alwaysPermit")?.asBoolean

            mysqlDatabase.update(PlayerInfoTable) {
                where { it.entryId eq entryId }

                downstreamUuid?.let { set(PlayerInfoTable.downstreamUuid, it.toStringUnsigned()) }
                downstreamName?.let { set(PlayerInfoTable.downstreamName, it) }
                alwaysPermit?.let { set(PlayerInfoTable.alwaysPermit, it) }
            }

            call.respond(ApiSuccessDataResponse())
        }
        post("/players/sync") {
            @Serializable
            data class Req(
                val data: Map<String, List<String>>,
            )

            val req = call.receive<Req>()

            supervisorScope {
                req.data.forEach { (service, value) ->
                    launch {
                        val ygService = YggdrasilServicesHolder.services[service] ?: return@launch
                        ygService.service.batchQuery(value).forEach { profile ->
                            UpstreamPlayerTransformEvent(
                                ygService, profile, profile, skipRestrictTest = true
                            ).eventFire()
                        }
                    }
                }
            }

            call.respond(ApiSuccessDataResponse())
        }

        delete("/players/{id}") {
            val id = call.parameters["id"] ?: throw ApiRejectedException("Missing entry id")
            mysqlDatabase.delete(PlayerInfoTable) { it.entryId eq id }
            call.respond(ApiSuccessDataResponse())
        }
    }
}