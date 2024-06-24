package com.kasukusakura.yggdrasilgateway.core.http.event

import io.ktor.server.routing.*

public class ApiRouteInitializeEvent(
    public val route: Route,
    public val authorization: Boolean,
)