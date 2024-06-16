package com.kasukusakura.yggdrasilgateway.api.internal

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal object GlobalEventBusCoroutineScopeDelegate : CoroutineScope {
    override var coroutineContext: CoroutineContext = EmptyCoroutineContext
}