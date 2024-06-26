package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.db

import org.ktorm.schema.Table
import org.ktorm.schema.varchar

internal object YggdrasilOptionsTable : Table<Nothing>("yggdrasil_options") {
    val key = varchar("key")
    val value = varchar("value")
}