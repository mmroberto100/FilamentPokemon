package com.mmunoz.filamentpokemon.core.domain.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class ResultTest {

    private val success: Result<Int, DataError.Network> = Result.Success(21)
    private val failure: Result<Int, DataError.Network> = Result.Error(DataError.Network.NO_INTERNET)

    @Test
    fun `map transforms success payload`() {
        val mapped = success.map { it * 2 }
        assertThat(mapped).isEqualTo(Result.Success(42))
    }

    @Test
    fun `map passes error through untouched`() {
        val mapped = failure.map { it * 2 }
        assertThat(mapped).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
    }

    @Test
    fun `onSuccess runs only for success and returns same result`() {
        var called = false
        val chained = success.onSuccess { called = true }
        assertThat(called).isTrue()
        assertThat(chained).isEqualTo(success)

        called = false
        failure.onSuccess { called = true }
        assertThat(called).isFalse()
    }

    @Test
    fun `onFailure runs only for error with the typed error`() {
        var received: DataError.Network? = null
        failure.onFailure { received = it }
        assertThat(received).isEqualTo(DataError.Network.NO_INTERNET)

        received = null
        success.onFailure { received = it }
        assertThat(received).isNull()
    }

    @Test
    fun `asEmptyResult drops payload but keeps outcome`() {
        assertThat(success.asEmptyResult()).isEqualTo(Result.Success(Unit))
        assertThat(failure.asEmptyResult()).isInstanceOf(Result.Error::class)
    }

    @Test
    fun `getOrNull and errorOrNull`() {
        assertThat(success.getOrNull()).isEqualTo(21)
        assertThat(success.errorOrNull()).isNull()
        assertThat(failure.getOrNull()).isNull()
        assertThat(failure.errorOrNull()).isEqualTo(DataError.Network.NO_INTERNET)
    }
}
