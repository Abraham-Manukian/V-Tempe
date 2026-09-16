@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vtempe.ui.*
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ScheduleStep(state: OnboardingState, presenter: OnboardingPresenter) {
    val dayLabels = listOf(
        "Mon" to stringResource(Res.string.day_mon_short),
        "Tue" to stringResource(Res.string.day_tue_short),
        "Wed" to stringResource(Res.string.day_wed_short),
        "Thu" to stringResource(Res.string.day_thu_short),
        "Fri" to stringResource(Res.string.day_fri_short),
        "Sat" to stringResource(Res.string.day_sat_short),
        "Sun" to stringResource(Res.string.day_sun_short)
    )
    StepTitle(stringResource(Res.string.label_weekdays))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dayLabels.forEach { (day, label) ->
            val selected = state.days[day] == true
            ModernToggleChip(
                label = label,
                selected = selected,
                onClick = { presenter.update { st -> st.copy(days = st.days + (day to !selected)) } }
            )
        }
    }
}
