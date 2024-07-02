package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.module.message.MessagesModule

@EventSubscriber
private object MessagesRegisterer {
    init {
        MessagesModule.registerTexts("gateway-yggdrasil/messages.properties")
    }
}