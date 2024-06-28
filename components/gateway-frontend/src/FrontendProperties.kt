package com.kasukusakura.yggdrasilgateway.frontend

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.properties.SimpleProperties

@EventSubscriber
internal object FrontendProperties : SimpleProperties("frontend") {
    var devUrl = ""
    var staticFilesLocation = ""
}