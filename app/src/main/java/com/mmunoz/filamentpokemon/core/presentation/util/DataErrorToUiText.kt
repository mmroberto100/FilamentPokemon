package com.mmunoz.filamentpokemon.core.presentation.util

import com.mmunoz.filamentpokemon.R
import com.mmunoz.filamentpokemon.core.domain.util.DataError

fun DataError.toUiText(): UiText = when (this) {
    DataError.Network.NO_INTERNET -> UiText.StringResource(R.string.error_no_internet)
    DataError.Network.REQUEST_TIMEOUT -> UiText.StringResource(R.string.error_timeout)
    DataError.Network.TOO_MANY_REQUESTS -> UiText.StringResource(R.string.error_too_many_requests)
    DataError.Network.UNAUTHORIZED -> UiText.StringResource(R.string.error_unauthorized)
    DataError.Network.FORBIDDEN -> UiText.StringResource(R.string.error_forbidden)
    DataError.Network.NOT_FOUND -> UiText.StringResource(R.string.error_not_found)
    DataError.Network.SERIALIZATION -> UiText.StringResource(R.string.error_serialization)
    DataError.Network.SERVER_ERROR,
    DataError.Network.SERVICE_UNAVAILABLE -> UiText.StringResource(R.string.error_server)
    DataError.Network.BAD_REQUEST,
    DataError.Network.CONFLICT,
    DataError.Network.PAYLOAD_TOO_LARGE,
    DataError.Network.UNKNOWN -> UiText.StringResource(R.string.error_unknown)

    DataError.Local.DISK_FULL -> UiText.StringResource(R.string.error_disk_full)
    DataError.Local.NOT_FOUND -> UiText.StringResource(R.string.error_file_not_found)
    DataError.Local.OVER_POLYGON_BUDGET -> UiText.StringResource(R.string.error_over_polygon_budget)
    DataError.Local.FILE_TOO_LARGE -> UiText.StringResource(R.string.error_file_too_large)
    DataError.Local.NO_GLB_ARCHIVE -> UiText.StringResource(R.string.error_no_glb_archive)
    DataError.Local.CORRUPT_FILE -> UiText.StringResource(R.string.error_corrupt_file)
    DataError.Local.UNKNOWN -> UiText.StringResource(R.string.error_unknown)
}
