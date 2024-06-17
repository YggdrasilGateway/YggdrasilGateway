package com.kasukusakura.yggdrasilgateway.api.util

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventBus

@DslMarker
private annotation class EventBusMarker

@EventBusMarker
public suspend inline fun <T : Any> T.eventFire(
    eventBus: EventBus = EventBus.GLOBAL_BUS,
    nofail: Boolean = true,
): T = apply {
    eventBus.fireEvent(this, nofail = nofail)
}
