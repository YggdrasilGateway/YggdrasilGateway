package com.kasukusakura.yggdrasilgateway.core.module.user.db

import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.varchar

internal object UserRoleTable : Table<Nothing>("user_role") {
    val userid = int("userid").primaryKey()
    val role = varchar("role").primaryKey()
}