package com.kasukusakura.yggdrasilgateway.api.eventbus

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class EventSubscriber {
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    public annotation class Handler(
        @get:JvmName("value")
        val priority: EventPriority = EventPriority.NORMAL,
    )
}

