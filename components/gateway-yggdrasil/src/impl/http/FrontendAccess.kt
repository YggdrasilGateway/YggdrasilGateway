package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.http

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonArray
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonObject
import com.kasukusakura.yggdrasilgateway.api.util.eventFire
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent
import com.kasukusakura.yggdrasilgateway.core.http.event.ApiRouteInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiFailedResponse
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiRejectedException
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiSuccessDataResponse
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.PlayerInfo
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.PlayerInfoTable
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.YggdrasilServicesTable
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.evt.UpstreamPlayerTransformEvent
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.OperationFlags
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.OperationOptionsSaver
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.YggdrasilServicesHolder
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.YggdrasilServicesHolder.sharedHttpClient
import com.kasukusakura.yggdrasilgateway.yggdrasil.remote.YggdrasilServiceProviders
import com.kasukusakura.yggdrasilgateway.yggdrasil.util.parseUuid
import com.kasukusakura.yggdrasilgateway.yggdrasil.util.toStringUnsigned
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
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
                        "connectionTimeout"(service.connectionTimeout)
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
            val connectionTimeout = conf.getAsJsonPrimitive("connectionTimeout")?.asLong ?: 0

            kotlin.runCatching {
                YggdrasilServiceProviders.constructService(urlPath)
            }.onFailure { err ->
                throw ApiRejectedException("No service available for $urlPath -> $err")
            }
            if (connectionTimeout < 0) {
                throw ApiRejectedException("Invalid timeout configuration")
            }

            val targetService = YggdrasilServicesHolder.services[id]
            if (targetService == null) {
                mysqlDatabase.insert(YggdrasilServicesTable) {
                    set(YggdrasilServicesTable.id, id)
                    set(YggdrasilServicesTable.urlPath, urlPath)
                    set(YggdrasilServicesTable.comment, comment)
                    set(YggdrasilServicesTable.active, active)
                    set(YggdrasilServicesTable.connection_timeout, connectionTimeout)
                }
            } else {
                mysqlDatabase.update(YggdrasilServicesTable) {
                    where { it.id eq id }
                    set(YggdrasilServicesTable.urlPath, urlPath)
                    set(YggdrasilServicesTable.comment, comment)
                    set(YggdrasilServicesTable.active, active)
                    set(YggdrasilServicesTable.connection_timeout, connectionTimeout)
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
            withContext(Dispatchers.IO) {
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

            withContext(Dispatchers.IO) {
                mysqlDatabase.update(PlayerInfoTable) {
                    where { it.entryId eq entryId }

                    downstreamUuid?.let { set(PlayerInfoTable.downstreamUuid, it.toStringUnsigned()) }
                    downstreamName?.let { set(PlayerInfoTable.downstreamName, it) }
                    alwaysPermit?.let { set(PlayerInfoTable.alwaysPermit, it) }
                }
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
                    launch(Dispatchers.IO) {
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
            withContext(Dispatchers.IO) { mysqlDatabase.delete(PlayerInfoTable) { it.entryId eq id } }
            call.respond(ApiSuccessDataResponse())
        }


        /// Operation Flags
        get("/system/flags") {
            call.respond(
                ApiSuccessDataResponse(
                    Gson().toJsonTree(YggdrasilServicesHolder.flags)
                )
            )
        }

        patch("/system/flags") {
            val req = call.receive<JsonObject>()

            val result = Gson().fromJson(req, OperationFlags::class.java)
                ?: throw ApiRejectedException("Failed to parse operations flags from $req")
            YggdrasilServicesHolder.flags = result
            withContext(Dispatchers.IO) { OperationOptionsSaver.saveOptions() }

            call.respond(ApiSuccessDataResponse())
        }

        post("/system/fetch-content") {
            val httpCall = sharedHttpClient.get(call.receive<JsonObject>().getAsJsonPrimitive("data").asString)
            if (httpCall.status.value != 200) {
                call.respond(ApiRejectedException(httpCall.status.toString()))
                httpCall.bodyAsChannel().cancel()
                return@post
            }
            if (httpCall.contentType()?.match(ContentType.Application.Json) != true) {
                call.respond(ApiRejectedException("yggdrasil.settings.delivered-public-key.sync.incorrect-content-type"))
                httpCall.bodyAsChannel().cancel()
                return@post
            }

            call.respond(object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    httpCall.bodyAsChannel().copyAndClose(channel)
                }
            })

        }

        get("/csv/export") {
            withContext(Dispatchers.IO) {
                call.respondTextWriter(contentType = ContentType.Text.CSV.withCharset(Charsets.UTF_8)) {
                    CSVWriter(this).use { csvWriter ->
                        val lines = arrayOf(
                            "entry-id",
                            "origin",
                            "upstream-name",
                            "upstream-uuid",
                            "downstream-name",
                            "downstream-uuid",
                            "always-permit",
                        )
                        csvWriter.writeNext(lines)
                        mysqlDatabase.sequenceOf(PlayerInfoTable).forEach { player ->
                            lines[0] = player.entryId
                            lines[1] = player.declaredYggdrasilTree
                            lines[2] = player.upstreamName
                            lines[3] = player.upstreamUuid
                            lines[4] = player.downstreamName
                            lines[5] = player.downstreamUuid
                            lines[6] = player.alwaysPermit.toString()
                            csvWriter.writeNext(lines)
                        }
                    }
                }
            }
        }
        post("/csv/import") {
            withContext(Dispatchers.IO) {

                val override = call.request.queryParameters["override"]?.toBoolean() ?: false
                var processed = false

                val multipartData = call.receiveMultipart()
                multipartData.forEachPart { part ->
                    try {
                        if (part.name == "files") {
                            if (processed) return@forEachPart
                            if (part !is PartData.FileItem) {
                                throw ApiRejectedException("Part `files` is not a file")
                            }

                            processed = true
                            mysqlDatabase.useTransaction { trans ->
                                trans.connection.createStatement()
                                    .use { statement -> statement.execute("LOCK TABLES yggdrasil_player_info WRITE") }
                                try {

                                    val effected = mutableSetOf<String>()
                                    val players = mysqlDatabase.sequenceOf(PlayerInfoTable)

                                    part.provider().asStream()
                                        .bufferedReader(part.contentType?.charset() ?: Charsets.UTF_8)
                                        .use { reader ->
                                            val csvReader = CSVReader(reader)
                                            val headers = csvReader.readNextSilently()
                                                ?: throw ApiRejectedException("Empty file found")

                                            val indexes = mutableMapOf<String, Int>()
                                            fun String.pushIndex() {
                                                indexes[this] = headers.indexOf(this)
                                                if (headers.indexOf(this) == -1) {
                                                    throw ApiRejectedException("Missing header $this")
                                                }
                                            }

                                            fun String.parseBool(): Boolean = when (lowercase()) {
                                                "yes", "true", "1", "on" -> true
                                                else -> false
                                            }

                                            "origin".pushIndex()
                                            "upstream-name".pushIndex()
                                            "upstream-uuid".pushIndex()
                                            "downstream-name".pushIndex()
                                            "downstream-uuid".pushIndex()
                                            "always-permit".pushIndex()

                                            while (true) {
                                                val nextLines = csvReader.readNext() ?: break

                                                val target = players
                                                    .filter { it.declaredYggdrasilTree eq nextLines[indexes["origin"]!!] }
                                                    .filter {
                                                        it.upstreamUuid eq nextLines[indexes["upstream-uuid"]!!].parseUuid()
                                                            .toStringUnsigned()
                                                    }
                                                    .firstOrNull()

                                                if (target == null) {
                                                    val entryId = YggdrasilServicesHolder.nextEntryIdWithTest()
                                                    players.add(PlayerInfo {
                                                        this.entryId = entryId
                                                        declaredYggdrasilTree = nextLines[indexes["origin"]!!]
                                                        upstreamName = nextLines[indexes["upstream-name"]!!]
                                                        upstreamUuid = nextLines[indexes["upstream-uuid"]!!].parseUuid()
                                                            .toStringUnsigned()

                                                        downstreamName = nextLines[indexes["downstream-name"]!!]
                                                        downstreamUuid =
                                                            nextLines[indexes["downstream-uuid"]!!].parseUuid()
                                                                .toStringUnsigned()

                                                        alwaysPermit = nextLines[indexes["always-permit"]!!].parseBool()
                                                    })
                                                    effected.add(entryId)
                                                } else {
                                                    effected.add(target.entryId)


                                                    target.downstreamName = nextLines[indexes["downstream-name"]!!]
                                                    target.downstreamUuid =
                                                        nextLines[indexes["downstream-uuid"]!!].parseUuid()
                                                            .toStringUnsigned()

                                                    target.alwaysPermit =
                                                        nextLines[indexes["always-permit"]!!].parseBool()

                                                    target.flushChanges()
                                                }
                                            }
                                        }

                                    if (override) {
                                        mysqlDatabase.from(PlayerInfoTable)
                                            .select(PlayerInfoTable.entryId)
                                            .forEach { row ->
                                                val entryId = row[PlayerInfoTable.entryId]!!
                                                if (!effected.contains(entryId)) {
                                                    mysqlDatabase.delete(PlayerInfoTable) { it.entryId eq entryId }
                                                }
                                            }
                                    }


                                    trans.commit()
                                } finally {
                                    trans.connection.createStatement()
                                        .use { statement -> statement.execute("UNLOCK TABLES") }
                                }
                            }
                        }
                    } finally {
                        part.dispose()
                    }
                }

                if (processed) {
                    call.respond(ApiSuccessDataResponse { })
                } else {
                    call.respond(ApiFailedResponse("No players.csv found.", 407))
                }
            }
        }
    }
}