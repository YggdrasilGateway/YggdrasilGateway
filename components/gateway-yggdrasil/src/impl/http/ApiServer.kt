package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.http

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonObject
import com.kasukusakura.yggdrasilgateway.api.util.eventFire
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.core.http.event.ApiRouteInitializeEvent
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiRejectedException
import com.kasukusakura.yggdrasilgateway.yggdrasil.data.PlayerProfile
import com.kasukusakura.yggdrasilgateway.yggdrasil.data.encode
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.PlayerInfo
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.PlayerInfoTable
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.evt.UpstreamPlayerTransformEvent
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.LoadedYggdrasilService
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.OperationFlags
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.YggdrasilServicesHolder
import com.kasukusakura.yggdrasilgateway.yggdrasil.util.parseUuid
import com.kasukusakura.yggdrasilgateway.yggdrasil.util.toStringUnsigned
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.ktorm.dsl.eq
import org.ktorm.entity.filter
import org.ktorm.entity.firstOrNull
import org.ktorm.entity.sequenceOf
import org.slf4j.LoggerFactory
import java.security.KeyPairGenerator
import java.util.*


@EventSubscriber
internal object ApiServer {
    private val log = LoggerFactory.getLogger(ApiServer::class.java)
    private val REJECTION_UUID = UUID(0, 0)

    @EventSubscriber.Handler
    fun ApiRouteInitializeEvent.handle() {
        if (authorization) return
        route.createRouteFromPath("/yggdrasil").mount()
    }

    private fun List<PlayerProfile.Property>.mergeGatewayInfo(
        service: LoadedYggdrasilService,
        old: PlayerProfile,
        entry: PlayerInfo?,
    ): List<PlayerProfile.Property> {
        return asSequence()
            .plus(PlayerProfile.Property("yggdrasil.gateway.source", service.id))
            .plus(PlayerProfile.Property("yggdrasil.gateway.origin.name", old.name ?: ""))
            .plus(PlayerProfile.Property("yggdrasil.gateway.origin.uuid", old.id?.toString() ?: ""))
            .plus(
                entry?.let {
                    sequenceOf(
                        PlayerProfile.Property("yggdrasil.gateway.entry", entry.entryId),
                        PlayerProfile.Property("yggdrasil.gateway.alwaysPermit", entry.alwaysPermit.toString()),
                    )
                } ?: emptySequence()
            )
            .toList()
    }

    private fun Route.mount() {
        get("/") {
            call.respondText {
                buildJsonObject {
                    "skinDomains" arr {}
                    val keypair = KeyPairGenerator.getInstance("RSA").apply {
                        initialize(2048)
                    }.generateKeyPair()
                    "signaturePublicKey"(buildString {
                        append("-----BEGIN PUBLIC KEY-----")
                        append(keypair.public.encoded.encodeBase64())
                        append("-----END PUBLIC KEY-----")
                    })
                }.toString()
            }
        }

        get("/sessionserver/session/minecraft/hasJoined") {
            val requestId = UUID.randomUUID().toString()
            log.debug(
                "[Yggdrasil/{}/hasJoined] Starting authorization with parameters... {}",
                requestId,
                call.request.queryParameters.formUrlEncode()
            )

            data class AuthorizedProfile(
                val service: LoadedYggdrasilService,
                val playerProfile: PlayerProfile,
            )

            val targetRequests = mutableMapOf<String, Deferred<Result<AuthorizedProfile?>>>()
            val requestJob = SupervisorJob()
            val authorizedProfile = CompletableDeferred<AuthorizedProfile?>()
            val awaiterTasks = mutableListOf<Job>()

            YggdrasilServicesHolder.services.values.forEach { service ->
                if (!service.active) return@forEach

                log.debug("[Yggdrasil/{}/hasJoined] Sending request with service {} {}", requestId, service.id, service)

                targetRequests[service.id] = async(Dispatchers.IO + requestJob) {
                    kotlin.runCatching {
                        service.service.hasJoined(params = call.request.queryParameters)?.let { profile ->
                            log.debug("[Yggdrasil/{}/hasJoined] Service {} replied {}", requestId, service.id, profile)

                            AuthorizedProfile(service, profile)
                        }
                    }.onFailure { err ->
                        log.warn(
                            "Exception when processing hasJoin with service {} -> {}",
                            service.id,
                            service.urlPath,
                            err
                        )
                    }
                }
            }


            when (val flag = YggdrasilServicesHolder.flags.processMode) {

                OperationFlags.ProcessMode.FIRST_SUCCESS -> {
                    targetRequests.values.forEach { task ->
                        awaiterTasks += launch(requestJob) {
                            task.await().getOrNull()?.let { authorizedProfile.complete(it) }
                        }
                    }
                    launch { awaiterTasks.forEach { it.join() }; authorizedProfile.complete(null) }
                }

                OperationFlags.ProcessMode.ERROR_SKIP, OperationFlags.ProcessMode.COMPLETE_TEST -> {
                    val skipError = flag == OperationFlags.ProcessMode.ERROR_SKIP
                    val results = mutableListOf<AuthorizedProfile>()

                    for (task in targetRequests.values) {
                        val result = task.await()
                        if (result.isFailure && !skipError) {
                            authorizedProfile.completeExceptionally(ApiRejectedException("Authorization servicing error."))
                        }
                        result.getOrNull()?.let { results.add(it) }
                    }

                    if (results.isEmpty()) {
                        log.debug(
                            "[Yggdrasil/{}/hasJoined] Authorization failed because no player profile available",
                            requestId,
                        )
                        authorizedProfile.complete(null)
                    } else if (results.size == 1) {
                        authorizedProfile.complete(results[0])
                    } else {
                        log.debug(
                            "[Yggdrasil/{}/hasJoined] Authorization failed because to many profile available: {}",
                            requestId,
                            authorizedProfile
                        )

                        authorizedProfile.completeExceptionally(ApiRejectedException("Multi authorization services replied."))
                    }
                }
            }

            kotlin.runCatching {
                val authResult = authorizedProfile.await()
                if (authResult == null) {
                    log.debug(
                        "[Yggdrasil/{}/hasJoined] Authorization failed because auth result is null",
                        requestId
                    )
                    call.respond(object : OutgoingContent.NoContent() {
                        override val status: HttpStatusCode get() = HttpStatusCode.NoContent
                    })
                    return@runCatching
                }

                log.debug(
                    "[Yggdrasil/{}/hasJoined] Authorized with {}, transforming {}",
                    requestId, authResult.service.id, authResult
                )
                withContext(Dispatchers.IO) {
                    val event = UpstreamPlayerTransformEvent(
                        service = authResult.service,
                        profile = authResult.playerProfile,
                        result = authResult.playerProfile,
                    ).eventFire(nofail = false)
                    val finalResult = event.result

                    call.respondText(
                        contentType = ContentType.Application.Json,
                    ) {
                        PlayerProfile(
                            id = finalResult.id,
                            name = finalResult.name,
                            properties = finalResult.properties.orEmpty().mergeGatewayInfo(
                                service = authResult.service,
                                old = authResult.playerProfile,
                                entry = if (event.targetPlayerInitialized) {
                                    event.targetPlayer
                                } else null
                            )
                        ).encode().toString().also { finalData ->
                            log.debug(
                                "[Yggdrasil/{}/hasJoined] Authorized with {}, final result {}",
                                requestId, authResult.service.id, finalData
                            )
                        }
                    }
                }
            }.onFailure { err ->
                if (err !is CancellationException) {
                    log.warn("Exception when processing authorization: {}", call.request.uri, err)
                }

                if (YggdrasilServicesHolder.flags.enchantedErrorRejection) {
                    call.respondText(
                        contentType = ContentType.Application.Json,
                    ) {
                        PlayerProfile(
                            id = REJECTION_UUID,
                            name = "\$REJECTION",
                            properties = listOf(
                                PlayerProfile.Property("rejection.reason", err.toString()),
                            )
                        ).encode().toString()
                    }
                } else {
                    call.respond(object : OutgoingContent.NoContent() {
                        override val status: HttpStatusCode get() = HttpStatusCode.NoContent
                    })
                }
            }
        }
        get("/sessionserver/session/minecraft/profile/{uuid}") {
            val uid = call.parameters["uuid"]?.parseUuid() ?: throw ApiRejectedException("UUID")

            val result = withContext(Dispatchers.IO) {
                mysqlDatabase.sequenceOf(PlayerInfoTable)
                    .filter { it.downstreamUuid eq uid.toStringUnsigned() }
                    .firstOrNull()
            }
            val upstream = result?.let { YggdrasilServicesHolder.services[it.declaredYggdrasilTree] }
            if (result == null || upstream == null) {
                call.respond(object : OutgoingContent.NoContent() {
                    override val status: HttpStatusCode get() = HttpStatusCode.NotFound
                })
                return@get
            }

            val resultProfile = withContext(Dispatchers.IO) {
                upstream.service.queryProfile(
                    result.upstreamUuid,
                    unsigned = call.request.queryParameters["unsigned"]?.toBoolean() ?: true,
                )
            }

            if (resultProfile == null) {
                call.respond(object : OutgoingContent.NoContent() {
                    override val status: HttpStatusCode get() = HttpStatusCode.NotFound
                })
                return@get
            }

            call.respondText(
                contentType = ContentType.Application.Json,
            ) {
                PlayerProfile(
                    id = result.downstreamUuid.parseUuid(),
                    name = result.downstreamName,
                    properties = resultProfile.properties.orEmpty().mergeGatewayInfo(
                        service = upstream,
                        old = resultProfile,
                        entry = result,
                    ),
                ).encode().toString()
            }

        }
        post("/api/profiles/minecraft") { }

        route("{...}") {
            handle { println(call.request.uri) }
        }
    }
}