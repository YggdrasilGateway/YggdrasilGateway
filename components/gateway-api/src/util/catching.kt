@file:OptIn(ExperimentalContracts::class)

package com.kasukusakura.yggdrasilgateway.api.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public inline fun <R> Result<R>.recoverSuppressed(transform: (exception: Throwable) -> R): Result<R> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }
    return when (val exception = exceptionOrNull()) {
        null -> this
        else -> try {
            Result.success(transform(exception))
        } catch (e: Throwable) {
            exception.addSuppressed(e)
            this
        }
    }
}

@JvmName("orElseNotNull")
public inline fun <R> Result<R>.orElse(transform: (exception: Throwable?) -> R): Result<R> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }
    return fold(
        onSuccess = { result ->
            result?.let { Result.success(it) } ?: kotlin.runCatching { transform(null) }
        },
        onFailure = { error ->
            try {
                Result.success(transform(error))
            } catch (e: Throwable) {
                error.addSuppressed(e)
                this
            }
        }
    )
}


public inline fun <R : Any> Result<R?>.orElse(transform: (exception: Throwable?) -> R): Result<R> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return fold(
        onSuccess = { result ->
            result?.let { Result.success(it) } ?: kotlin.runCatching { transform(null) }
        },
        onFailure = { error ->
            try {
                Result.success(transform(error))
            } catch (e: Throwable) {
                error.addSuppressed(e)
                Result.failure(error)
            }
        }
    )
}
