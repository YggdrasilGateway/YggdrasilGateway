package com.kasukusakura.yggdrasilgateway.core.util

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventPriority
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.event.DatabaseInitializationEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

private val stateLock = ReentrantReadWriteLock()

@field:Volatile
private var databaseReady = false

private val pendingCoroutines = ConcurrentLinkedQueue<Continuation<Unit>>()

@EventSubscriber
private object EnsureDatabaseStateHolder {
    @EventSubscriber.Handler(priority = EventPriority.MONITOR)
    fun onDatabaseReady(evt: DatabaseInitializationEvent) {
        stateLock.write {
            databaseReady = true
        }

        pendingCoroutines.removeIf { it.resume(Unit); true }
    }
}


@Suppress("RemoveExplicitTypeArguments", "RedundantSuppression")
public suspend fun ensureDatabaseAvailable() {
    if (databaseReady) {
        return
    }

    val readLock = stateLock.readLock()
    readLock.lock()

    if (databaseReady) {
        readLock.unlock()
        return
    }

    suspendCancellableCoroutine<Unit> { continuation ->
        pendingCoroutines.add(continuation)

        readLock.unlock()
    }
}