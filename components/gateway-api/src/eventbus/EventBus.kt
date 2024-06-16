package com.kasukusakura.yggdrasilgateway.api.eventbus

import com.kasukusakura.yggdrasilgateway.api.internal.GlobalEventBusCoroutineScopeDelegate
import com.kasukusakura.yggdrasilgateway.api.tracking.CallingStack
import com.kasukusakura.yggdrasilgateway.api.tracking.tracking
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.Continuation

public class EventBus(
    public val monitorScope: CoroutineScope,
) {
    public companion object {
        public val GLOBAL_BUS: EventBus = EventBus(GlobalEventBusCoroutineScopeDelegate)

        private val log = LoggerFactory.getLogger(EventBus::class.java)
    }

    private val allHandlers =
        EnumMap<EventPriority, ConcurrentLinkedDeque<ProcessorRegistry>>(EventPriority::class.java)
            .also { map ->
                EventPriority.entries.forEach { priority -> map[priority] = ConcurrentLinkedDeque() }
            }

    public fun registerProcessor(container: Any, listener: Any, processor: EventProcessor): EventProcessorHandle {
        val handlers = allHandlers[processor.priority]!!
        val registry = ProcessorRegistry(container, listener, processor, handlers)
        handlers.add(registry)
        return registry
    }

    public fun getAllHandlesByContainer(container: Any): List<EventProcessorHandle> =
        allHandlers.values.asSequence().flatMap { it.asSequence() }
            .filter { it.container == container }
            .toList()

    public fun getAllHandlesByListener(listener: Any): List<EventProcessorHandle> =
        allHandlers.values.asSequence().flatMap { it.asSequence() }
            .filter { it.listener == listener }
            .toList()

    public suspend fun fireEvent(event: Any, nofail: Boolean = true) {
        tracking(comment = "Event broadcasting", event) {
            allHandlers.forEach { (priority, listeners) ->
                if (priority == EventPriority.MONITOR) {
                    val subtrack = coroutineContext[CallingStack]!!.next(context = "Monitor Event Fire")

                    listeners.forEach { listener ->
                        if (listener.processor.acceptable(event)) {
                            monitorScope.launch(subtrack) {
                                tracking("Event Processor", listener) {
                                    kotlin.runCatching {
                                        with(listener.processor) { processEvent(event) }
                                    }.onFailure { error ->
                                        if (!nofail) {
                                            coroutineContext[CoroutineExceptionHandler]?.let { eh ->
                                                eh.handleException(coroutineContext, error)
                                                return@onFailure
                                            }
                                        }

                                        log.warn(
                                            "Exception when processing event {} in context {}",
                                            event,
                                            coroutineContext[CallingStack],
                                            error
                                        )
                                    }
                                }
                            }
                        }
                    }

                } else {
                    listeners.forEach { listener ->
                        if (listener.processor.acceptable(event)) {
                            tracking("Event Processor", listener) {
                                if (nofail) {
                                    kotlin.runCatching {
                                        with(listener.processor) { processEvent(event) }
                                    }.onFailure { error ->
                                        log.warn(
                                            "Exception when processing event {} in context {}",
                                            event,
                                            coroutineContext[CallingStack],
                                            error
                                        )
                                    }
                                } else {
                                    with(listener.processor) { processEvent(event) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    public fun registerListener(container: Any, listener: Any) {
        val listenerKlass = listener.javaClass
        generateSequence<Class<*>>(listenerKlass) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filterNot { it.isSynthetic || it.isBridge }
            .filter { it.isAnnotationPresent(EventSubscriber.Handler::class.java) }
            .onEach { it.isAccessible = true }
            .forEach { method ->
                kotlin.runCatching {
                    log.debug("Registering {}", method)
                    registerListener(container, listener, method)
                }.onFailure { error ->
                    getAllHandlesByListener(listener).forEach { it.dispose() }

                    throw RuntimeException("Exception when registering $method", error)
                }
            }
    }


    private fun registerListener(container: Any, listener: Any, method: Method) {
        var handle = MethodHandles.lookup().unreflect(method)
        if (!Modifier.isStatic(method.modifiers)) {
            handle = handle.bindTo(listener)
        }
        val isSuspend = handle.type().lastParameterType() == Continuation::class.java
        if (!isSuspend) {
            handle = MethodHandles.dropArguments(handle, handle.type().parameterCount(), Continuation::class.java)
        }

        if (handle.type().parameterType(0) != CoroutineScope::class.java) {
            handle = MethodHandles.dropArguments(handle, 0, CoroutineScope::class.java)
        }

        if (handle.type().parameterCount() != 3) {
            // CoroutineScope, Event, Continuation
            error("Excepting (CoroutineScope, *, Continuation)* but found " + handle.type())
        }

        val eventType = handle.type().parameterType(1)
        handle = handle.asType(
            MethodType.methodType(
                Any::class.java,
                CoroutineScope::class.java,
                Any::class.java,
                Continuation::class.java
            )
        )
        val anno = method.getAnnotation(EventSubscriber.Handler::class.java)
        registerProcessor(container, listener, MethodHandleProcessor(handle, eventType, anno.priority))
    }
}

private class ProcessorRegistry(
    override val container: Any,
    override val listener: Any,
    val processor: EventProcessor,

    val handlers: MutableCollection<ProcessorRegistry>,
) : EventProcessorHandle {
    override fun dispose() {
        handlers.remove(this)
    }

    override fun toString(): String {
        return "EventProcessor(container=$container, listener=$listener, processor=$processor)"
    }
}
