package com.kasukusakura.yggdrasilgateway.core.module.message

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonObject
import com.kasukusakura.yggdrasilgateway.core.module.message.submodule.DbAccess
import java.util.*
import java.util.concurrent.ConcurrentHashMap


public object MessagesModule {
    private val defaultTexts = ConcurrentHashMap<String, String>()
    private val gson = Gson()
    private val stringMapToken = object : TypeToken<Map<String, String>>() {}

    public val defaultsTextSnapshot: JsonObject
        get() = buildJsonObject {
            defaultTexts.forEach {
                put(it.key, it.value)
            }
        }


    public fun registerTexts(resourcePath: String) {
        (Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            ?: error("No resource found at $resourcePath with " + Thread.currentThread().contextClassLoader)).bufferedReader().use { reader ->

            val map = when {
                resourcePath.endsWith(".properties") -> {
                    val prop = Properties().apply { load(reader) }
                    @Suppress("UNCHECKED_CAST")
                    prop as Map<String, String>
                }
                resourcePath.endsWith(".json") -> {
                    gson.fromJson(reader, stringMapToken)
                }

                else -> error("Unknown how to read $resourcePath as message bundle")
            }

            registerTexts(map)
        }
    }

    public fun registerTexts(texts: Map<String, String>) {
        texts.forEach { (key, value) ->
            if (defaultTexts.putIfAbsent(key, value) != null) {
                defaultTexts.keys.removeAll(texts.keys)

                error("Duplicated text key: $key")
            }
        }
    }

    public operator fun get(key: String): String {
        return DbAccess.loadedTexts[key] ?: defaultTexts[key] ?: key
    }
}