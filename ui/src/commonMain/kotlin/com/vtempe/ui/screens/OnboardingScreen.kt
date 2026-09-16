@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.*
import com.vtempe.ui.LocalAppLocaleUpdater
import com.vtempe.ui.presenter.ONBOARDING_TOTAL_STEPS
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingError
import com.vtempe.ui.screens.onboarding.*
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingScreen(
    onDone: () -> Unit = {},
    presenter: OnboardingPresenter = rememberOnboardingPresenter()
) {
    val state by presenter.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val updateLocale = LocalAppLocaleUpdater.current

    LaunchedEffect(state.languageTag) {
        updateLocale(state.languageTag.takeIf { it != "system" })
    }

    val progress = (state.currentStep + 1).toFloat() / ONBOARDING_TOTAL_STEPS.toFloat()
    val isLastStep = state.currentStep >= ONBOARDING_TOTAL_STEPS - 1

    BackHandlerCompat(enabled = state.currentStep > 0 || state.saving) {
        // Match the on-screen Back button; keep the saving flow on screen until it completes.
        if (!state.saving) presenter.prevStep()
    }

    BrandScreen(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { focusManager.clearFocus() }
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.onboard_title),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    stringResource(Res.string.onboard_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.onboard_step_counter).kmpFormat(
                        state.currentStep + 1,
                        ONBOARDING_TOTAL_STEPS
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    trackColor = Color.White.copy(alpha = 0.25f),
                    color = AiPalette.PrimaryBright
                )
            }

            Crossfade(
                targetState = state.currentStep,
                label = "onboarding_steps",
                modifier = Modifier.fillMaxWidth()
            ) { step ->
                StepCard {
                    when (step) {
                        0 -> LanguageStep(state, presenter)

                        1 -> BodyMeasurementsStep(state, presenter)

                        2 -> GoalStep(state, presenter)

                        3 -> CoachStep(state, presenter)

                        4 -> TrainingModeStep(state, presenter)

                        5 -> TrainingFocusStep(state, presenter)

                        6 -> SessionDurationStep(state, presenter)

                        7 -> EquipmentStep(state, presenter)

                        8 -> DietStep(state, presenter)

                        9 -> InjuriesStep(state, presenter)

                        10 -> BudgetStep(state, presenter)

                        11 -> ActivityStep(state, presenter)

                        12 -> SplitStep(state, presenter)

                        else -> ScheduleStep(state, presenter)
                    }
                }
            }

            state.error?.let { error ->
                Text(
                    text = stringResource(when (error) {
                        OnboardingError.INVALID_INPUT -> Res.string.error_invalid_input
                        OnboardingError.SAVE_FAILED -> Res.string.onboard_save_error
                        OnboardingError.GENERATION_FAILED -> Res.string.onboard_generation_error
                    }),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.currentStep > 0) {
                    OutlinedButton(
                        onClick = { presenter.prevStep() },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f))
                    ) { Text(stringResource(Res.string.action_back), fontWeight = FontWeight.Bold) }
                }

                Button(
                    onClick = {
                        if (isLastStep) presenter.save(onDone) else presenter.nextStep()
                    },
                    modifier = Modifier.weight(1.5f).heightIn(min = 56.dp),
                    enabled = !state.saving,
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AiPalette.DeepAccent,
                        contentColor = AiPalette.OnDeepAccent
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Text(
                        if (isLastStep) stringResource(Res.string.onboard_cta) else stringResource(Res.string.action_next),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        if (state.saving) {
            SavingOverlay(savingStep = state.savingStep)
        }
    }
}
