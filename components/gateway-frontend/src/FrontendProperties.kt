package com.kasukusakura.yggdrasilgateway.frontend

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.properties.SimpleProperties

@EventSubscriber
internal object FrontendProperties : SimpleProperties("frontend") {
    var devUrl = "http://127.0.0.1:35774"
}