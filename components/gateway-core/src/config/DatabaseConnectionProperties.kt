package com.kasukusakura.yggdrasilgateway.core.config

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.api.properties.SimpleProperties

@EventSubscriber
internal object DatabaseConnectionProperties : SimpleProperties("database") {
    var mysqlJdbc = "jdbc:mysql://localhost:3306/yggdrasilgateway?useUnicode=true&characterEncoding=UTF-8"
    var mysqlUsername: String = "root"
    var mysqlPassword: String = "root"
}
