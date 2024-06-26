package com.kasukusakura.yggdrasilgateway.yggdrasil.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonObject
import com.kasukusakura.yggdrasilgateway.yggdrasil.util.parseUuid
import com.kasukusakura.yggdrasilgateway.yggdrasil.util.toStringUnsigned
import java.util.*

public class PlayerProfile(
    public var id: UUID? = null,
    public var name: String? = null,
    public var properties: List<Property>? = null,
) {
    public class Property(
        public var name: String,
        public var value: String,
        public var signature: String? = null,
    )

    public companion object {
        public fun parse(content: String): PlayerProfile = parse(JsonParser.parseString(content).asJsonObject)

        public fun parse(content: JsonObject): PlayerProfile {
            return PlayerProfile(
                name = content.getAsJsonPrimitive("name")?.asString,
                id = content.getAsJsonPrimitive("id")?.asString?.parseUuid(),
                properties = content.getAsJsonArray("properties")?.map { prop ->
                    prop as JsonObject
                    Property(
                        name = prop.getAsJsonPrimitive("name").asString,
                        value = prop.getAsJsonPrimitive("value").asString,
                        signature = prop.getAsJsonPrimitive("signature")?.asString
                    )
                }
            )
        }
    }

    override fun toString(): String {
        return encode().toString()
    }
}

public fun PlayerProfile.encode(): JsonObject = buildJsonObject {
    id?.let { "id"(it.toStringUnsigned()) }
    name?.let { "name"(it) }
    properties?.let { prop ->
        "properties" arr {
            prop.forEach { value ->
                +buildJsonObject {
                    "name"(value.name)
                    "value"(value.value)
                    value.signature?.let { "signature"(it) }
                }
            }
        }
    }
}
