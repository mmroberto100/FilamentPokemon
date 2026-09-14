package com.mmunoz.filamentpokemon.viewer.filament

/** Why [FilamentModelRenderer] could not bring a `.glb` on screen. */
enum class ModelLoadError {
    /** The file could not be read (missing, purged by the OS, not accessible). */
    FileUnreadable,
    /** gltfio rejected the bytes; the file is not a usable glTF-Binary container. */
    ParseFailed,
    /** The parsed model's resources never became resident within [FilamentModelRenderer.LOAD_TIMEOUT_MS]. */
    Timeout
}
