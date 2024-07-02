package com.kasukusakura.yggdrasilgateway.yggdrasil.db

import org.ktorm.schema.Table
import org.ktorm.schema.boolean
import org.ktorm.schema.long
import org.ktorm.schema.varchar

internal object YggdrasilServicesTable : Table<Nothing>("yggdrasil_services") {
    val id = varchar("id").primaryKey()
    val urlPath = varchar("urlPath")
    val comment = varchar("comment")
    val active = boolean("active")
    val connection_timeout = long("connection_timeout")
}