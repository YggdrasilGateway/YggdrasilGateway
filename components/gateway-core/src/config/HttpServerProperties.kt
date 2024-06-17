package com.kasukusakura.yggdrasilgateway.core.config

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.properties.SimpleProperties

@EventSubscriber
internal object HttpServerProperties : SimpleProperties("http-server") {
    var host = ""
    var port = 8080
}