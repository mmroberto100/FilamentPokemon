package com.mmunoz.filamentpokemon.core.presentation.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** A string that may come from a resource (localizable) or be fully dynamic. */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    class StringResource(
        @param:StringRes val id: Int,
        val args: Array<Any> = emptyArray()
    ) : UiText

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> stringResource(id, *args)
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> context.getString(id, *args)
    }
}
