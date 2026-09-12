package com.mmunoz.filamentpokemon.search.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmunoz.filamentpokemon.R
import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import java.text.NumberFormat

private const val SLIDER_STEP = 1_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSheet(
    maxFaceCount: Int,
    onMaxFaceCountChange: (Int) -> Unit,
    onMaxFaceCountChangeFinished: () -> Unit,
    onDismiss: () -> Unit
) {
    val formatter = NumberFormat.getIntegerInstance()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.budget_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.budget_sheet_value, formatter.format(maxFaceCount)),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = maxFaceCount.toFloat(),
                onValueChange = { onMaxFaceCountChange(it.roundToStep()) },
                onValueChangeFinished = onMaxFaceCountChangeFinished,
                valueRange = PolygonBudget.MIN_FACES.toFloat()..PolygonBudget.MAX_FACES.toFloat(),
                steps = (PolygonBudget.MAX_FACES - PolygonBudget.MIN_FACES) / SLIDER_STEP - 1,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    R.string.budget_sheet_range,
                    formatter.format(PolygonBudget.MIN_FACES),
                    formatter.format(PolygonBudget.MAX_FACES)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.budget_sheet_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Float.roundToStep(): Int = (Math.round(this / SLIDER_STEP) * SLIDER_STEP)
