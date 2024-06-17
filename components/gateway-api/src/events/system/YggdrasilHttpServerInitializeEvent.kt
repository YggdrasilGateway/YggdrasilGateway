package com.kasukusakura.yggdrasilgateway.api.events.system

import io.ktor.server.application.*
import io.ktor.server.engine.*

public object YggdrasilHttpServerInitializeEvent {
    public class EnvironmentInitializeEvent(public val envBuilder: ApplicationEngineEnvironmentBuilder)
    public class ModuleInitializeEvent(public val app: Application)
}
