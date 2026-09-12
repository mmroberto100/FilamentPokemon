package com.mmunoz.filamentpokemon.search.presentation.mappers

import com.mmunoz.filamentpokemon.core.domain.model.PokemonModel
import com.mmunoz.filamentpokemon.search.presentation.PokemonModelUi
import java.text.NumberFormat

fun PokemonModel.toUi(maxFaceCount: Int): PokemonModelUi = PokemonModelUi(
    uid = uid,
    name = name,
    author = author,
    thumbnailUrl = thumbnailUrl,
    faceCount = faceCount,
    formattedFaceCount = NumberFormat.getIntegerInstance().format(faceCount),
    budgetUsage = if (maxFaceCount > 0) faceCount.toFloat() / maxFaceCount else 1f,
    isAnimated = isAnimated,
    licenseLabel = licenseLabel
)
