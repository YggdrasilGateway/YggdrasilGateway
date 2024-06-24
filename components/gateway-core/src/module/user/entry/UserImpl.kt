package com.kasukusakura.yggdrasilgateway.core.module.user.entry

import com.google.gson.JsonObject
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonArray
import com.kasukusakura.yggdrasilgateway.core.module.user.principal.UserPrincipal

internal class UserImpl(
    override val userid: Int,
    override var username: String,
    var email: String?,
    var active: Boolean,
    var roles: Set<String>,
) : UserPrincipal {
    override val displayName: String
        get() = username

    override fun hasPermission(permission: String): Boolean {
        roles.forEach { role ->
            val roleImpl = UserEntryManager.roles[role] ?: return@forEach
            if (roleImpl.hasPermission(permission)) return true
        }
        return false
    }

    override fun reportInformation(output: JsonObject) {
        output.addProperty("user.userid", userid)
        output.addProperty("user.username", username)
        output.addProperty("user.email", email)
        output.addProperty("user.active", active)
        output.add("user.roles", buildJsonArray {
            roles.forEach { role -> add(role) }
        })
    }
}