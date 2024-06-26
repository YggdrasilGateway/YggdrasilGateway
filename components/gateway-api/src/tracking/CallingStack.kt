package com.kasukusakura.yggdrasilgateway.api.tracking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@TrackingDsl
public class CallingStack(
    public val parent: CallingStack? = null,
    public val context: Any? = null,
    public val comment: Any? = null,
) : AbstractCoroutineContextElement(CallingStack) {
    public companion object : CoroutineContext.Key<CallingStack>

    override fun toString(): String {
        return context.toString()
    }

    public fun fullToString(): StringBuilder {
        val builder = StringBuilder()

        fun emit(stack: CallingStack) {
            stack.parent?.let {
                emit(it)
                builder.append(" - ")
            }
            comment?.let { builder.append(it).append(" ") }
            builder.append(context)
        }
        emit(this)
        return builder
    }

    @TrackingDsl
    public fun next(
        comment: Any? = null,
        context: Any?,
    ): CallingStack = CallingStack(
        context = context,
        comment = comment,
        parent = this,
    )
}

@DslMarker
private annotation class TrackingDsl

@TrackingDsl
public suspend fun <T> tracking(
    comment: Any? = null,
    context: Any?,
    block: suspend CoroutineScope.() -> T,
): T {
    val newStack = CallingStack(
        context = context,
        comment = comment,
        parent = coroutineContext[CallingStack],
    )
    try {
        return withContext(
            block = block,
            context = coroutineContext + newStack,
        )
    } catch (e: TrackingException) {
        throw e
    } catch (e: Error) {
        throw e
    } catch (e: Throwable) {
        if (e is TrackingIgnoredException) throw e

        throw TrackingException(newStack, e)
    }
}

public interface TrackingIgnoredException
