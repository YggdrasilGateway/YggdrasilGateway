package com.kasukusakura.yggdrasilgateway.core.module.user.db

import org.ktorm.schema.Table
import org.ktorm.schema.varchar

internal object RolePermissionsTable : Table<Nothing>("role_permissions") {
    val role = varchar("role")
    val perm = varchar("permission")
}