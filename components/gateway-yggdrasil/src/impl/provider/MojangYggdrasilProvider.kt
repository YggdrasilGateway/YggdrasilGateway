package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.provider

import com.google.gson.JsonParser
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.yggdrasil.data.PlayerProfile
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.YggdrasilServicesHolder.sharedHttpClient
import com.kasukusakura.yggdrasilgateway.yggdrasil.remote.YggdrasilService
import com.kasukusakura.yggdrasilgateway.yggdrasil.remote.YggdrasilServiceProvider
import com.kasukusakura.yggdrasilgateway.yggdrasil.remote.YggdrasilServiceProviders
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@EventSubscriber
private object MojangYggdrasilProvider : YggdrasilServiceProvider, YggdrasilService {
    init {
        YggdrasilServiceProviders.register(this)
    }

    override val priority: Int get() = 0

    override fun apply(basePath: String): YggdrasilService? {
        if (basePath != "mojang") return null

        return this
    }

    override suspend fun hasJoined(params: Parameters): PlayerProfile? {
        return sharedHttpClient.get("https://sessionserver.mojang.com/session/minecraft/hasJoined") {
            url.parameters.appendAll(params)
        }.toProfile()
    }

    private suspend fun HttpResponse.toProfile(): PlayerProfile? {
        if (status.value == HttpStatusCode.NoContent.value) return null
        if (status.value == 404) return null

        val content = bodyAsText()
        if (status.value != 200) error(content)

        return PlayerProfile.parse(content)
    }

    override suspend fun queryProfile(uuid: String, unsigned: Boolean): PlayerProfile? {
        return sharedHttpClient.get("https://sessionserver.mojang.com/session/minecraft/profile/$uuid?unsigned=$unsigned")
            .toProfile()
    }

    override suspend fun batchQuery(name: List<String>): List<PlayerProfile> {
        val result = sharedHttpClient.post("https://api.minecraftservices.com/minecraft/profile/lookup/bulk/byname") {
            val text = Json.encodeToString(ListSerializer(String.serializer()), name).encodeToByteArray()

            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentType: ContentType
                    get() = ContentType.Application.Json.withCharset(Charsets.UTF_8)

                override val contentLength: Long
                    get() = text.size.toLong()

                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeFully(text)
                }
            })
        }

        if (result.status.value == HttpStatusCode.NoContent.value) return emptyList()
        if (result.status.value == 404) return emptyList()

        val content = result.bodyAsText()
        if (result.status.value != 200) error(content)

        return JsonParser.parseString(content).asJsonArray
            .map { PlayerProfile.parse(it.asJsonObject) }
    }
}