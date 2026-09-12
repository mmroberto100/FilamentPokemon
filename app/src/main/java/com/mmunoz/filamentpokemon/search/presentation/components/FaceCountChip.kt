package com.mmunoz.filamentpokemon.search.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmunoz.filamentpokemon.R
import com.mmunoz.filamentpokemon.ui.theme.BudgetHigh
import com.mmunoz.filamentpokemon.ui.theme.BudgetLow
import com.mmunoz.filamentpokemon.ui.theme.BudgetMid

/** Face count badge coloured by how much of the polygon budget the model consumes. */
@Composable
fun FaceCountChip(
    formattedFaceCount: String,
    budgetUsage: Float,
    modifier: Modifier = Modifier
) {
    val background = when {
        budgetUsage < 0.5f -> BudgetLow
        budgetUsage < 0.85f -> BudgetMid
        else -> BudgetHigh
    }
    Text(
        text = stringResource(R.string.search_faces_chip, formattedFaceCount),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
