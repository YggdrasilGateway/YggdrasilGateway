package com.kasukusakura.yggdrasilgateway.api.eventbus

import kotlinx.coroutines.CoroutineScope
import java.lang.invoke.MethodHandle
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

internal class MethodHandleProcessor(
    // (CoroutineScope, Any, Continuation)Any
    private val handle: MethodHandle,
    private val eventType: Class<*>,
    override val priority: EventPriority,
) : EventProcessor {
    override fun acceptable(event: Any): Boolean = eventType.isInstance(event)

    override suspend fun CoroutineScope.processEvent(event: Any) {
        suspendCoroutineUninterceptedOrReturn<Unit> { cont ->
            @Suppress("USELESS_CAST")
            handle.invoke(this@processEvent, event, cont) as Any?
        }
    }
}