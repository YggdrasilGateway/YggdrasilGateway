package com.kasukusakura.yggdrasilgateway.api.event

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventBus
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventProcessor
import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.tracking.TrackingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventBusTest {
    @Test
    fun testEventProcess() = runTest {
        val eventBus = EventBus(this.backgroundScope)
        var eventProcessed = false
        eventBus.registerProcessor(Any(), Any(), object : EventProcessor {
            override fun acceptable(event: Any): Boolean = true
            override suspend fun CoroutineScope.processEvent(event: Any) {
                println("Event received! $event")
                eventProcessed = true
            }
        })

        eventBus.fireEvent(Any(), nofail = true)
        assertTrue("Event handler not called") { eventProcessed }
    }

    @Test
    fun testEventNoFail() = runTest {
        val eventBus = EventBus(this.backgroundScope)
        eventBus.registerProcessor(Any(), Any(), object : EventProcessor {
            override fun acceptable(event: Any): Boolean = true
            override suspend fun CoroutineScope.processEvent(event: Any) {
                println("Event received! $event")
                throw RuntimeException("TEST ERROR")
            }
        })

        eventBus.fireEvent(Any(), nofail = true)
        eventBus.fireEvent(Any())
    }

    @Test
    fun testEventFailing() = runTest {
        val eventBus = EventBus(this.backgroundScope)
        eventBus.registerProcessor(Any(), Any(), object : EventProcessor {
            override fun acceptable(event: Any): Boolean = true
            override suspend fun CoroutineScope.processEvent(event: Any) {
                println("Event received! $event")
                throw RuntimeException("TEST ERROR")
            }
        })

        assertThrows<TrackingException> {
            eventBus.fireEvent(Any(), nofail = false)
        }.printStackTrace(System.out)
    }

    @Test
    fun testEventListenerRegister() = runTest {
        val eventBus = EventBus(this.backgroundScope)
        var eventProcessed = 0
        eventBus.registerListener(Any(), object {
            @EventSubscriber.Handler
            fun test(event: Any) {
                println("Event received! $event")
                eventProcessed++
            }

            @EventSubscriber.Handler
            fun CoroutineScope.test(event: Any) {
                println("Event scope received! $event")
                eventProcessed++
            }

            @EventSubscriber.Handler
            suspend fun test1(event: Any) {
                println("Event suspend received! $event")
                eventProcessed++
            }

            @EventSubscriber.Handler
            suspend fun CoroutineScope.test1(event: Any) {
                println("Event suspend scope received! $event")
                eventProcessed++
            }

            // not calling
            @EventSubscriber.Handler
            fun notCalling(event: System) {
            }

            @EventSubscriber.Handler
            fun CoroutineScope.notCalling(event: System) {
            }

            @EventSubscriber.Handler
            suspend fun notCalling1(event: System) {
            }

            @EventSubscriber.Handler
            suspend fun CoroutineScope.notCalling1(event: System) {
            }
        })

        eventBus.fireEvent(Any(), nofail = false)
        assertEquals(4, eventProcessed, "Event handler not called")
    }
}