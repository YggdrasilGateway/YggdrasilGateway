package com.kasukusakura.yggdrasilgateway.core.module.user.db

import org.ktorm.schema.Table
import org.ktorm.schema.varchar

internal object RoleTable : Table<Nothing>("roles") {
    val role = varchar("role").primaryKey()
    val desc = varchar("desc")
}