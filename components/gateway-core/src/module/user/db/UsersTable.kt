package com.kasukusakura.yggdrasilgateway.core.module.user.db

import org.ktorm.schema.*

internal object UsersTable : Table<Nothing>("users") {
    val userid = int("userid").primaryKey()
    val username = varchar("username")
    val email = varchar("email")
    val password = bytes("password")
    val passwordSalt = bytes("passwordSalt")
    val active = boolean("active")
    val reactiveTime = long("reactiveTime")
    val creationTime = long("creationTime")
}