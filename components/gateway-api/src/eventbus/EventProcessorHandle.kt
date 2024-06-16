package com.kasukusakura.yggdrasilgateway.api.eventbus

public interface EventProcessorHandle {
    public val container: Any
    public val listener: Any

    public fun dispose()
}