package com.dhananjay.livecast.core

sealed interface LiveCastResult<out T> {
    data class Success<T>(val value: T) : LiveCastResult<T>
    data class Failure(val error: LiveCastError) : LiveCastResult<Nothing>
}

data class LiveCastError(
    val message: String,
    val cause: Throwable? = null
)
