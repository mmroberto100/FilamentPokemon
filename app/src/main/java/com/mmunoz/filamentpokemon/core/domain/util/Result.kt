package com.mmunoz.filamentpokemon.core.domain.util

/**
 * Typed success/failure wrapper used across all layers. Expected failures are never thrown,
 * they are returned as [Result.Error] with a typed [E].
 */
sealed interface Result<out D, out E : Error> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Error<out E : com.mmunoz.filamentpokemon.core.domain.util.Error>(val error: E) : Result<Nothing, E>
}

typealias EmptyResult<E> = Result<Unit, E>

inline fun <T, E : Error, R> Result<T, E>.map(map: (T) -> R): Result<R, E> {
    return when (this) {
        is Result.Error -> Result.Error(error)
        is Result.Success -> Result.Success(map(data))
    }
}

inline fun <T, E : Error> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    return when (this) {
        is Result.Error -> this
        is Result.Success -> {
            action(data)
            this
        }
    }
}

inline fun <T, E : Error> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> {
    return when (this) {
        is Result.Error -> {
            action(error)
            this
        }
        is Result.Success -> this
    }
}

fun <T, E : Error> Result<T, E>.asEmptyResult(): EmptyResult<E> {
    return map { }
}

/** Convenience for call sites that only care about the payload. */
fun <T, E : Error> Result<T, E>.getOrNull(): T? = (this as? Result.Success)?.data

fun <T, E : Error> Result<T, E>.errorOrNull(): E? = (this as? Result.Error)?.error
