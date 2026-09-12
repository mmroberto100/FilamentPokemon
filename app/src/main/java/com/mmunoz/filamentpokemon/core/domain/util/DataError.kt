package com.mmunoz.filamentpokemon.core.domain.util

sealed interface DataError : Error {

    enum class Network : DataError {
        BAD_REQUEST,
        REQUEST_TIMEOUT,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        TOO_MANY_REQUESTS,
        NO_INTERNET,
        PAYLOAD_TOO_LARGE,
        SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        SERIALIZATION,
        UNKNOWN
    }

    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        /** Model exceeds the user's triangle budget – rejected before any download starts. */
        OVER_POLYGON_BUDGET,
        /** The .glb archive is larger than the mobile download cap. */
        FILE_TOO_LARGE,
        /** Sketchfab offers no .glb archive for this model. */
        NO_GLB_ARCHIVE,
        /** The file on disk is not a complete glTF-Binary container (truncated, purged or not a .glb). */
        CORRUPT_FILE,
        UNKNOWN
    }
}
