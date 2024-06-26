package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlConnectionSource
import com.kasukusakura.yggdrasilgateway.core.database.DatabaseConnectionManager.mysqlDatabase
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.db.YggdrasilOptionsTable
import org.ktorm.dsl.forEach
import org.ktorm.dsl.from
import org.ktorm.dsl.select

internal object OperationOptionsSaver {

    fun reloadOptions() {
        val obj = JsonObject()
        mysqlDatabase.from(YggdrasilOptionsTable)
            .select(YggdrasilOptionsTable.key, YggdrasilOptionsTable.value)
            .forEach { row ->
                obj.add(row[YggdrasilOptionsTable.key], JsonParser.parseString(row[YggdrasilOptionsTable.value]))
            }

        val flags = Gson().fromJson(obj, OperationFlags::class.java)
        YggdrasilServicesHolder.flags = flags
    }

    fun saveOptions() {
        val jsonTree = Gson().toJsonTree(YggdrasilServicesHolder.flags)

        mysqlConnectionSource.connection.use { connection ->
            connection.prepareStatement("REPLACE INTO yggdrasil_options(`key`, `value`) VALUES(?, ?)").use { ps ->
                jsonTree.asJsonObject.entrySet().forEach { (key, value) ->
                    ps.setString(1, key)
                    ps.setString(2, value.toString())
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }

}