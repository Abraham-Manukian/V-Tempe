@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens

import com.vtempe.ui.*
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Sex
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingScreen(
    onDone: () -> Unit = {},
    presenter: OnboardingPresenter = rememberOnboardingPresenter()
) {
    val state by presenter.state.collectAsState()

    val equipmentOptions = listOf(
        stringResource(Res.string.equipment_dumbbells),
        stringResource(Res.string.equipment_barbell),
        stringResource(Res.string.equipment_kettlebell),
        stringResource(Res.string.equipment_bands),
        stringResource(Res.string.equipment_bench),
        stringResource(Res.string.equipment_pullup_bar),
        stringResource(Res.string.equipment_trx),
        stringResource(Res.string.equipment_mat),
        stringResource(Res.string.equipment_cardio)
    )
    val dayLabels = listOf(
        "Mon" to stringResource(Res.string.day_mon_short),
        "Tue" to stringResource(Res.string.day_tue_short),
        "Wed" to stringResource(Res.string.day_wed_short),
        "Thu" to stringResource(Res.string.day_thu_short),
        "Fri" to stringResource(Res.string.day_fri_short),
        "Sat" to stringResource(Res.string.day_sat_short),
        "Sun" to stringResource(Res.string.day_sun_short)
    )
    val languageOptions = listOf(
        "system" to stringResource(Res.string.settings_language_system),
        "en-US" to stringResource(Res.string.language_en),
        "ru-RU" to stringResource(Res.string.language_ru)
    )
    val trainingModeOptions = listOf(
        TRAINING_MODE_GYM to stringResource(Res.string.training_mode_gym),
        TRAINING_MODE_HOME to stringResource(Res.string.training_mode_home),
        TRAINING_MODE_OUTDOOR to stringResource(Res.string.training_mode_outdoor),
        TRAINING_MODE_MIXED to stringResource(Res.string.training_mode_mixed)
    )

    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AiPalette.Primary,
        unfocusedBorderColor = AiPalette.Outline.copy(alpha = 0.25f),
        cursorColor = AiPalette.Primary,
        focusedLabelColor = AiPalette.Primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = AiPalette.SurfaceLight.copy(alpha = 0.4f)
    )

    val progress = (state.currentStep + 1).toFloat() / ONBOARDING_TOTAL_STEPS.toFloat()
    val isLastStep = state.currentStep >= ONBOARDING_TOTAL_STEPS - 1

    BrandScreen(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                        0 -> {
                            StepTitle(stringResource(Res.string.label_language))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                languageOptions.forEach { (tag, label) ->
                                    ModernChip(
                                        selected = state.languageTag == tag,
                                        label = label,
                                        onClick = { presenter.setLanguage(tag) }
                                    )
                                }
                            }
                        }

                        1 -> {
                            StepTitle(stringResource(Res.string.settings_title))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = state.age,
                                    onValueChange = { v -> presenter.update { st -> st.copy(age = v.filter { it.isDigit() }.take(2)) } },
                                    label = { Text(stringResource(Res.string.label_age)) },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = inputColors
                                )
                                OutlinedTextField(
                                    value = state.heightCm,
                                    onValueChange = { v -> presenter.update { st -> st.copy(heightCm = v.filter { it.isDigit() }.take(3)) } },
                                    label = { Text(stringResource(Res.string.label_height_cm)) },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = inputColors
                                )
                                OutlinedTextField(
                                    value = state.weightKg,
                                    onValueChange = { v -> presenter.update { st -> st.copy(weightKg = v.filter { it.isDigit() || it == '.' }.take(5)) } },
                                    label = { Text(stringResource(Res.string.label_weight_kg)) },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = inputColors
                                )
                            }

                            Text(
                                stringResource(Res.string.label_sex),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Sex.entries.forEach { option ->
                                    val label = when (option) {
                                        Sex.MALE -> stringResource(Res.string.sex_male)
                                        Sex.FEMALE -> stringResource(Res.string.sex_female)
                                        Sex.OTHER -> stringResource(Res.string.sex_other)
                                    }
                                    ModernChip(
                                        selected = state.sex == option,
                                        label = label,
                                        onClick = { presenter.update { it.copy(sex = option) } }
                                    )
                                }
                            }
                        }

                        2 -> {
                            StepTitle(stringResource(Res.string.label_goal))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Goal.entries.forEach { goal ->
                                    val label = when (goal) {
                                        Goal.LOSE_FAT -> stringResource(Res.string.goal_lose_fat)
                                        Goal.MAINTAIN -> stringResource(Res.string.goal_maintain)
                                        Goal.GAIN_MUSCLE -> stringResource(Res.string.goal_gain_muscle)
                                    }
                                    ModernChip(
                                        selected = state.goal == goal,
                                        label = label,
                                        onClick = { presenter.update { it.copy(goal = goal) } }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(Res.string.label_experience).kmpFormat(state.experienceLevel),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Slider(
                                value = state.experienceLevel.toFloat(),
                                onValueChange = { lvl -> presenter.update { it.copy(experienceLevel = lvl.toInt().coerceIn(1, 5)) } },
                                valueRange = 1f..5f,
                                steps = 3,
                                colors = SliderDefaults.colors(
                                    thumbColor = AiPalette.Primary,
                                    activeTrackColor = AiPalette.Primary,
                                    inactiveTrackColor = AiPalette.Primary.copy(alpha = 0.2f)
                                )
                            )
                        }

                        3 -> {
                            StepTitle(stringResource(Res.string.label_coach_trainer))
                            Text(
                                text = stringResource(Res.string.coach_trainer_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                coachTrainerOptions.forEach { coach ->
                                    CoachChoiceCard(
                                        coach = coach,
                                        selected = state.coachTrainerId == coach.id,
                                        onClick = {
                                            presenter.update {
                                                it.copy(coachTrainerId = coach.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        4 -> {
                            StepTitle(stringResource(Res.string.label_training_mode))
                            Text(
                                text = stringResource(Res.string.training_mode_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                trainingModeOptions.forEach { (mode, label) ->
                                    ModernChip(
                                        selected = state.trainingMode == mode,
                                        label = label,
                                        onClick = { presenter.update { it.copy(trainingMode = mode) } }
                                    )
                                }
                            }
                        }

                        5 -> {
                            StepTitle(stringResource(Res.string.label_equipment_presets))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                equipmentOptions.forEach { option ->
                                    ModernToggleChip(
                                        label = option,
                                        selected = state.selectedEquipment.contains(option),
                                        onClick = { presenter.toggleEquipment(option) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = state.customEquipment,
                                onValueChange = { presenter.setCustomEquipment(it) },
                                label = { Text(stringResource(Res.string.label_equipment_manual)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = inputColors
                            )
                        }

                        6 -> {
                            StepTitle(stringResource(Res.string.label_dietary_prefs))
                            OutlinedTextField(
                                value = state.dietaryPreferences,
                                onValueChange = { presenter.update { st -> st.copy(dietaryPreferences = it) } },
                                label = { Text(stringResource(Res.string.label_dietary_prefs)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = inputColors
                            )
                            OutlinedTextField(
                                value = state.allergies,
                                onValueChange = { presenter.update { st -> st.copy(allergies = it) } },
                                label = { Text(stringResource(Res.string.label_allergies)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = inputColors
                            )
                        }

                        else -> {
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
                    }
                }
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

            if (state.saving) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onBackground, strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(text = stringResource(Res.string.settings_saving), color = MaterialTheme.colorScheme.onBackground)
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun StepCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ModernChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AiPalette.Primary,
            selectedLabelColor = AiPalette.OnGradient,
            containerColor = AiPalette.SurfaceLight,
            labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        ),
        border = null,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
private fun CoachChoiceCard(
    coach: CoachTrainerUi,
    selected: Boolean,
    onClick: () -> Unit
) {
    val coachName = stringResource(coach.nameRes)
    val borderColor = if (selected) AiPalette.Primary else AiPalette.Outline.copy(alpha = 0.18f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) AiPalette.Primary.copy(alpha = 0.10f) else AiPalette.SurfaceLight)
            .border(2.dp, borderColor, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Image(
            painter = painterResource(coach.avatar),
            contentDescription = coachName,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = coachName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(Res.string.coach_trainer_avatar_hint),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ModernToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) AiPalette.Primary else AiPalette.SurfaceLight
    val textColor = if (selected) AiPalette.OnGradient else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}
