@file:OptIn(ExperimentalContracts::class)

package com.kasukusakura.yggdrasilgateway.api.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@DslMarker
internal annotation class GsonBuilderDsl

@JvmInline
public value class JsonObjectBuilder public constructor(
    private val delegate: JsonObject
) {
    // @formatter:off

    @GsonBuilderDsl public fun put(key: String, value: String?): Unit = delegate.addProperty(key, value)
    @GsonBuilderDsl public fun put(key: String, value: Number?): Unit = delegate.addProperty(key, value)
    @GsonBuilderDsl public fun put(key: String, value: Boolean?): Unit = delegate.addProperty(key, value)
    @GsonBuilderDsl public fun put(key: String, value: Char?): Unit = delegate.addProperty(key, value)
    @GsonBuilderDsl public fun put(key: String, value: JsonElement?): Unit = delegate.add(key, value)


    @GsonBuilderDsl public operator fun String.invoke(value: String?): Unit = delegate.addProperty(this, value)
    @GsonBuilderDsl public operator fun String.invoke(value: Number?): Unit = delegate.addProperty(this, value)
    @GsonBuilderDsl public operator fun String.invoke(value: Boolean?): Unit = delegate.addProperty(this, value)
    @GsonBuilderDsl public operator fun String.invoke(value: Char?): Unit = delegate.addProperty(this, value)
    @GsonBuilderDsl public operator fun String.invoke(value: JsonElement?): Unit = delegate.add(this, value)

    @GsonBuilderDsl public infix fun String.value(value: String?): Unit = delegate.addProperty(this, value)
    @GsonBuilderDsl public infix fun String.value(value: Number?): Unit = delegate.addProperty(this, value)
    @GsonBuilderDsl public infix fun String.value(value: Boolean?): Unit = delegate.addProperty(this, value)
    @GsonBuilderDsl public infix fun String.value(value: Char?): Unit = delegate.addProperty(this, value)
    @GsonBuilderDsl public infix fun String.value(value: JsonElement?): Unit = delegate.add(this, value)



    // @formatter:on

    @GsonBuilderDsl
    public fun String.invoke(block: JsonObjectBuilder.() -> Unit) {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        delegate.add(this, buildJsonObject(block))
    }

    @GsonBuilderDsl
    public infix fun String.obj(block: JsonObjectBuilder.() -> Unit) {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        delegate.add(this, buildJsonObject(block))
    }

    @GsonBuilderDsl
    public infix fun String.arr(block: JsonArrayBuilder.() -> Unit) {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        delegate.add(this, buildJsonArray(block))
    }
}

@JvmInline
public value class JsonArrayBuilder public constructor(
    private val delegate: JsonArray
) {
    public operator fun String?.unaryPlus(): Unit = delegate.add(this)
    public operator fun Number?.unaryPlus(): Unit = delegate.add(this)
    public operator fun Boolean?.unaryPlus(): Unit = delegate.add(this)
    public operator fun Char?.unaryPlus(): Unit = delegate.add(this)
    public operator fun JsonElement?.unaryPlus(): Unit = delegate.add(this)

    // @formatter:off
    @GsonBuilderDsl public fun add(value: String?): Unit = delegate.add(value)
    @GsonBuilderDsl public fun add(value: Number?): Unit = delegate.add(value)
    @GsonBuilderDsl public fun add(value: Boolean?): Unit = delegate.add(value)
    @GsonBuilderDsl public fun add(value: Char?): Unit = delegate.add(value)
    @GsonBuilderDsl public fun add(value: JsonElement?): Unit = delegate.add(value)
    // @formatter:on


    @GsonBuilderDsl
    public infix fun obj(block: JsonObjectBuilder.() -> Unit) {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        delegate.add(buildJsonObject(block))
    }

    @GsonBuilderDsl
    public infix fun arr(block: JsonArrayBuilder.() -> Unit) {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        delegate.add(buildJsonArray(block))
    }
}

@GsonBuilderDsl
public inline fun buildJsonObject(block: JsonObjectBuilder.() -> Unit): JsonObject {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return JsonObject().also { JsonObjectBuilder(it).also(block) }
}

@GsonBuilderDsl
public inline fun buildJsonArray(block: JsonArrayBuilder.() -> Unit): JsonArray {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return JsonArray().also { JsonArrayBuilder(it).also(block) }
}
