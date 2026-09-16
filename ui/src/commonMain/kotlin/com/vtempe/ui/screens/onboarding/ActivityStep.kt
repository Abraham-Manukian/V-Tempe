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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.vtempe.shared.domain.model.LifestyleActivity
import com.vtempe.ui.*
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ActivityStep(state: OnboardingState, presenter: OnboardingPresenter) {
    StepTitle(stringResource(Res.string.label_lifestyle))
    Text(
        text = stringResource(Res.string.lifestyle_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = onCard.copy(alpha = 0.72f)
    )
    Spacer(Modifier.height(4.dp))
    val lifestyleOptions = listOf(
        LifestyleActivity.SEDENTARY to
            (stringResource(Res.string.lifestyle_sedentary) to stringResource(Res.string.lifestyle_sedentary_desc)),
        LifestyleActivity.LIGHT to
            (stringResource(Res.string.lifestyle_light) to stringResource(Res.string.lifestyle_light_desc)),
        LifestyleActivity.ACTIVE to
            (stringResource(Res.string.lifestyle_active) to stringResource(Res.string.lifestyle_active_desc)),
        LifestyleActivity.VERY_ACTIVE to
            (stringResource(Res.string.lifestyle_very_active) to stringResource(Res.string.lifestyle_very_active_desc))
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lifestyleOptions.forEach { (activity, labels) ->
            val (label, desc) = labels
            val selected = state.lifestyleActivity == activity
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { presenter.update { it.copy(lifestyleActivity = activity) } },
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
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = onCard.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
