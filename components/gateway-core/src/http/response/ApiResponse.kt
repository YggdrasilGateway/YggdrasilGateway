@file:OptIn(ExperimentalContracts::class)

package com.kasukusakura.yggdrasilgateway.core.http.response

import com.google.gson.JsonElement
import com.kasukusakura.yggdrasilgateway.api.tracking.TrackingIgnoredException
import com.kasukusakura.yggdrasilgateway.api.util.JsonObjectBuilder
import com.kasukusakura.yggdrasilgateway.api.util.buildJsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public sealed class ApiResponse

public open class ApiFailedResponse(
    public open val message: String,
    public val errorCode: Int,
) : ApiResponse() {
    public fun doThrow(): Nothing = throw AsException()

    private inner class AsException : ApiRejectedException(message, errorCode) {
        override fun toResponse(): ApiFailedResponse = this@ApiFailedResponse
    }
}

public open class ApiSuccessResponse : ApiResponse()

public class ApiSuccessDataResponse(
    public val response: JsonElement? = null,
) : ApiResponse()

public inline fun ApiSuccessDataResponse(block: JsonObjectBuilder.() -> Unit): ApiSuccessDataResponse {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return ApiSuccessDataResponse(buildJsonObject(block))
}

public open class ApiRejectedException(
    public override val message: String,
    public open val errorCode: Int = 403,
) : RuntimeException(null, null, false, false), TrackingIgnoredException {
    public open fun toResponse(): ApiFailedResponse = ApiFailedResponse(message, errorCode)
}
