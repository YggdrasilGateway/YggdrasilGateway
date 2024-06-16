package com.kasukusakura.yggdrasilgateway.api.eventbus

import kotlinx.coroutines.CoroutineScope

public interface EventProcessor {
    public val priority: EventPriority get() = EventPriority.NORMAL

    public fun acceptable(event: Any): Boolean

    public suspend fun CoroutineScope.processEvent(event: Any)
}