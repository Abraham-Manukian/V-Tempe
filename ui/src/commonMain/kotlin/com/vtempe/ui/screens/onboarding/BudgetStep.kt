@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.*
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BudgetStep(state: OnboardingState, presenter: OnboardingPresenter) {
    StepTitle(stringResource(Res.string.label_budget))
    Text(
        text = stringResource(Res.string.budget_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = onCard.copy(alpha = 0.72f)
    )
    val budgetOptions = listOf(
        1 to stringResource(Res.string.budget_low),
        2 to stringResource(Res.string.budget_medium),
        3 to stringResource(Res.string.budget_high)
    )
    val budgetDescriptions = mapOf(
        1 to stringResource(Res.string.budget_low_desc),
        2 to stringResource(Res.string.budget_medium_desc),
        3 to stringResource(Res.string.budget_high_desc)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        budgetOptions.forEach { (level, label) ->
            val selected = state.budgetLevel == level
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { presenter.update { it.copy(budgetLevel = level) } },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) AiPalette.Primary.copy(alpha = 0.12f)
                    else Color.White
                ),
                border = if (selected) BorderStroke(2.dp, AiPalette.Primary)
                    else BorderStroke(1.dp, AiPalette.Outline.copy(alpha = 0.25f)),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) AiPalette.Primary else onCard
                    )
                    Text(
                        budgetDescriptions[level] ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = onCard.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
