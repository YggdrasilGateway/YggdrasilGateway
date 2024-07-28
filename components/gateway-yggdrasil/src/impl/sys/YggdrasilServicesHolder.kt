package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventPriority
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent
import com.kasukusakura.yggdrasilgateway.core.http.response.ApiRejectedException
import com.kasukusakura.yggdrasilgateway.core.module.message.MessagesModule
import com.kasukusakura.yggdrasilgateway.yggdrasil.data.PlayerProfile
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.PlayerInfo
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.PlayerInfoTable
import com.kasukusakura.yggdrasilgateway.yggdrasil.db.YggdrasilServicesTable
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.codesnippet.YggdrasilCodeSnippets
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.evt.UpstreamPlayerTransformEvent
import com.kasukusakura.yggdrasilgateway.yggdrasil.util.parseUuid
import com.kasukusakura.yggdrasilgateway.yggdrasil.util.toStringUnsigned
import io.ktor.client.*
import org.ktorm.dsl.eq
import org.ktorm.dsl.forEach
import org.ktorm.dsl.from
import org.ktorm.dsl.select
import org.ktorm.entity.*
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.asKotlinRandom
import io.ktor.client.engine.java.Java as JavaEngine

@EventSubscriber
internal object YggdrasilServicesHolder {
    private val secureRandom = SecureRandom().asKotlinRandom()
    val services = ConcurrentHashMap<String, LoadedYggdrasilService>()
    val sharedHttpClient = HttpClient(JavaEngine)
    val entityIdTemplate = sequenceOf(
        '0'..'9',
        'a'..'z',
        'A'..'Z',
    ).flatten().joinToString(separator = "")

    var flags = OperationFlags()

    @EventSubscriber.Handler
    fun reloadServices(event: DatabaseInitializationEvent) {
        val newServices = mutableMapOf<String, LoadedYggdrasilService>()
        mysqlDatabase.from(YggdrasilServicesTable)
            .select(
                YggdrasilServicesTable.id,
                YggdrasilServicesTable.comment,
                YggdrasilServicesTable.urlPath,
                YggdrasilServicesTable.active,
                YggdrasilServicesTable.limited,
                YggdrasilServicesTable.connection_timeout,
            )
            .forEach { result ->
                val service = LoadedYggdrasilService(
                    id = result[YggdrasilServicesTable.id]!!,
                    urlPath = result[YggdrasilServicesTable.urlPath]!!,
                    comment = result[YggdrasilServicesTable.comment],
                    active = result[YggdrasilServicesTable.active]!!,
                    limited = result[YggdrasilServicesTable.limited]!!,
                    connectionTimeout = result[YggdrasilServicesTable.connection_timeout]!!
                )
                newServices[service.id] = service
            }

        services.putAll(newServices)
        services.keys.removeIf { !newServices.containsKey(it) }

        OperationOptionsSaver.reloadOptions()
        OperationOptionsSaver.saveOptions()
    }


    internal fun generateEntryId(size: Int = 32) = buildString(size) {
        repeat(size) { append(entityIdTemplate.random(secureRandom)) }
    }

    fun nextEntryIdWithTest(): String {
        var newEntryId: String
        do {
            newEntryId = generateEntryId()
        } while (mysqlDatabase.sequenceOf(PlayerInfoTable).any { it.entryId eq newEntryId })
        return newEntryId
    }

    @EventSubscriber.Handler(priority = EventPriority.HIGHEST)
    fun UpstreamPlayerTransformEvent.process() {
        mysqlDatabase.useTransaction { trans ->
            trans.connection.autoCommit = false

            val dbResult = mysqlDatabase.sequenceOf(PlayerInfoTable)
                .filter { it.declaredYggdrasilTree eq service.id }
                .filter { it.upstreamUuid eq profile.id!!.toStringUnsigned() }
                .firstOrNull()

            if (dbResult != null) {
                if (dbResult.upstreamName != profile.name) {
                    dbResult.upstreamName = profile.name!!
                    dbResult.flushChanges()
                }
                if (!skipRestrictTest && !dbResult.alwaysPermit) {
                    if (flags.prohibitMode) {
                        throw ApiRejectedException(MessagesModule["yggdrasil.prohibit.prohibit-reject"])
                    }
                    if (service.limited) {
                        throw ApiRejectedException(MessagesModule["yggdrasil.prohibit.service-limited"])
                    }
                }

                targetPlayer = dbResult
                result = PlayerProfile(
                    id = dbResult.downstreamUuid.parseUuid(),
                    name = dbResult.downstreamName,
                    properties = profile.properties,
                )
                return
            }

            if (flags.prohibitMode && !skipRestrictTest) {
                throw ApiRejectedException(MessagesModule["yggdrasil.prohibit.prohibit-reject"])
            }

            var newEntryId: String
            do {
                newEntryId = generateEntryId()
            } while (mysqlDatabase.sequenceOf(PlayerInfoTable).any { it.entryId eq newEntryId })


            val upstreamName = profile.name!!
            val upstreamUuid = profile.id!!
            var newName = upstreamName
            val triedGeneratedNames = mutableSetOf<String>()
            var triedNameGenerateCount = 0

            while (mysqlDatabase.sequenceOf(PlayerInfoTable).any { it.downstreamNameIgnoreCase eq newName }) {
                if (!flags.autoResolveName) {
                    throw ApiRejectedException("Player name already been used.")
                }

                newName = YggdrasilCodeSnippets.nameConflictResolver.getCodeSnippet()
                    .next(upstreamName, upstreamUuid, triedGeneratedNames)

                triedGeneratedNames.add(newName)
                triedNameGenerateCount++
                if (triedNameGenerateCount > 40) {
                    // TODO: push warn
                    throw ApiRejectedException("Player name already been used.")
                }
            }


            var newUuid = upstreamUuid
            val triedGeneratedUuids = mutableSetOf<UUID>()
            var triedUuidGenerateCount = 0
            while (mysqlDatabase.sequenceOf(PlayerInfoTable).any { it.downstreamUuid eq newUuid.toStringUnsigned() }) {
                if (!flags.autoResolveUuidConflict) {
                    throw ApiRejectedException("Player uuid $newUuid conflict")
                }

                newUuid = YggdrasilCodeSnippets.uuidConflictResolver.getCodeSnippet()
                    .next(upstreamName, upstreamUuid, triedGeneratedUuids)

                triedGeneratedUuids.add(newUuid)
                triedUuidGenerateCount++
                if (triedUuidGenerateCount > 40) {
                    // TODO: push warn
                    throw ApiRejectedException("Player name already been used.")
                }
            }

            val newEntry = PlayerInfo {
                entryId = newEntryId
                alwaysPermit = false

                declaredYggdrasilTree = service.id

                this.upstreamName = profile.name!!
                this.upstreamUuid = profile.id!!.toStringUnsigned()

                downstreamName = newName
                downstreamUuid = newUuid.toStringUnsigned()
            }
            mysqlDatabase.sequenceOf(PlayerInfoTable).add(newEntry)

            targetPlayer = newEntry
            result = PlayerProfile(
                id = newUuid,
                name = newName,
                properties = profile.properties,
            )
        }
    }
}