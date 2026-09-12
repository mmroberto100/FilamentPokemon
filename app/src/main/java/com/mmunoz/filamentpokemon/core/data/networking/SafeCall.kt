package com.mmunoz.filamentpokemon.core.data.networking

import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

/**
 * Typed GET: `httpClient.get<SearchResponseDto>(route = "search", queryParameters = mapOf(...))`.
 * Null query values are skipped so optional parameters (e.g. `cursor`) need no branching at call sites.
 */
suspend inline fun <reified Response : Any> HttpClient.get(
    route: String,
    queryParameters: Map<String, Any?> = mapOf()
): Result<Response, DataError.Network> {
    return safeCall {
        get {
            url(constructRoute(route))
            queryParameters.forEach { (key, value) ->
                if (value != null) parameter(key, value)
            }
        }
    }
}

/**
 * Executes a Ktor call and folds transport/serialization exceptions into [DataError.Network].
 * [CancellationException] is always rethrown so structured concurrency keeps working.
 */
suspend inline fun <reified T> safeCall(
    execute: () -> HttpResponse
): Result<T, DataError.Network> {
    val response = try {
        execute()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return Result.Error(e.toNetworkError())
    }
    return responseToResult(response)
}

suspend inline fun <reified T> responseToResult(
    response: HttpResponse
): Result<T, DataError.Network> {
    return when (response.status.value) {
        in 200..299 -> try {
            Result.Success(response.body<T>())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e.toNetworkError())
        }
        400 -> Result.Error(DataError.Network.BAD_REQUEST)
        401 -> Result.Error(DataError.Network.UNAUTHORIZED)
        403 -> Result.Error(DataError.Network.FORBIDDEN)
        404 -> Result.Error(DataError.Network.NOT_FOUND)
        408 -> Result.Error(DataError.Network.REQUEST_TIMEOUT)
        409 -> Result.Error(DataError.Network.CONFLICT)
        413 -> Result.Error(DataError.Network.PAYLOAD_TOO_LARGE)
        429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
        503 -> Result.Error(DataError.Network.SERVICE_UNAVAILABLE)
        in 500..599 -> Result.Error(DataError.Network.SERVER_ERROR)
        else -> Result.Error(DataError.Network.UNKNOWN)
    }
}

/** Maps the exceptions Ktor + OkHttp raise on Android to the closest [DataError.Network]. */
fun Exception.toNetworkError(): DataError.Network = when (this) {
    is HttpRequestTimeoutException,
    is io.ktor.client.network.sockets.ConnectTimeoutException,
    is io.ktor.client.network.sockets.SocketTimeoutException,
    is SocketTimeoutException -> DataError.Network.REQUEST_TIMEOUT

    is UnresolvedAddressException,
    is UnknownHostException -> DataError.Network.NO_INTERNET

    is SerializationException,
    is ContentConvertException -> DataError.Network.SERIALIZATION

    // Connection refused/reset, SSL handshake, airplane mode etc. all surface as IOException.
    is IOException -> DataError.Network.NO_INTERNET

    else -> DataError.Network.UNKNOWN
}
