package com.kasukusakura.yggdrasilgateway.api.util

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

public fun CoroutineScope.childrenScope(
    name: String = "",
    context: CoroutineContext = EmptyCoroutineContext,
    supervisor: Boolean = false,
): CoroutineScope {
    return CoroutineScope(
        coroutineContext.childrenContext(
            name = name,
            context = context,
            supervisor = supervisor,
        )
    )
}

public fun CoroutineContext.childrenContext(
    name: String = "",
    context: CoroutineContext = EmptyCoroutineContext,
    supervisor: Boolean = false,
): CoroutineContext {
    return (this + context + if (supervisor) {
        SupervisorJob(this[Job])
    } else {
        Job(this[Job])
    }).let { result ->
        if (name.isEmpty()) {
            result
        } else {
            result + CoroutineName(name)
        }
    }
}