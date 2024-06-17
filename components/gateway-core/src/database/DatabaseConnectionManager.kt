package com.kasukusakura.yggdrasilgateway.core.database

import com.kasukusakura.yggdrasilgateway.core.config.DatabaseConnectionProperties
import com.zaxxer.hikari.HikariDataSource
import org.ktorm.database.Database

public object DatabaseConnectionManager {
    public val mysqlConnectionSource: HikariDataSource by lazy {
        HikariDataSource().apply {
            jdbcUrl = DatabaseConnectionProperties.mysqlJdbc
            username = DatabaseConnectionProperties.mysqlUsername
            password = DatabaseConnectionProperties.mysqlPassword
        }
    }
    public val mysqlDatabase: Database by lazy { Database.connect(mysqlConnectionSource) }
}