package com.kasukusakura.yggdrasilgateway.api.events.system

import kotlinx.coroutines.CoroutineScope

public class RootCoroutineScopeInitializeEvent(
    public val coroutineScope: CoroutineScope,
)
