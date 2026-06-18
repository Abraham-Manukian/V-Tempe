@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens

import com.vtempe.ui.*
import com.vtempe.ui.presenter.ONBOARDING_TOTAL_STEPS
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import com.vtempe.ui.presenter.TRAINING_MODE_GYM
import com.vtempe.ui.presenter.TRAINING_MODE_HOME
import com.vtempe.ui.presenter.TRAINING_MODE_OUTDOOR
import com.vtempe.ui.presenter.TRAINING_MODE_MIXED
import com.vtempe.ui.presenter.TRAINING_FOCUS_STRENGTH
import com.vtempe.ui.presenter.TRAINING_FOCUS_HYPERTROPHY
import com.vtempe.ui.presenter.TRAINING_FOCUS_GENERAL
import com.vtempe.ui.presenter.TRAINING_FOCUS_FAT_LOSS
import com.vtempe.shared.domain.model.LifestyleActivity
import com.vtempe.shared.domain.model.SplitPreference
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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

// Fixed dark text — card/chip backgrounds are always light regardless of system dark theme
private val onCard = Color(0xFF1A1A1A)

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
        unfocusedLabelColor = onCard.copy(alpha = 0.6f),
        focusedTextColor = onCard,
        unfocusedTextColor = onCard,
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
                                color = onCard
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
                                color = onCard
                            )
                            val expLabel = when (state.experienceLevel) {
                                1 -> stringResource(Res.string.experience_level_1)
                                2 -> stringResource(Res.string.experience_level_2)
                                3 -> stringResource(Res.string.experience_level_3)
                                4 -> stringResource(Res.string.experience_level_4)
                                else -> stringResource(Res.string.experience_level_5)
                            }
                            Text(
                                expLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AiPalette.Primary
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
                            Text(
                                stringResource(Res.string.experience_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = onCard.copy(alpha = 0.6f)
                            )
                        }

                        3 -> {
                            StepTitle(stringResource(Res.string.label_coach_trainer))
                            Text(
                                text = stringResource(Res.string.coach_trainer_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onCard.copy(alpha = 0.72f)
                            )

                            val initialPage = coachTrainerOptions
                                .indexOfFirst { it.id == state.coachTrainerId }
                                .coerceAtLeast(0)
                            val pagerState = rememberPagerState(initialPage = initialPage) {
                                coachTrainerOptions.size
                            }

                            LaunchedEffect(pagerState.currentPage) {
                                presenter.update {
                                    it.copy(coachTrainerId = coachTrainerOptions[pagerState.currentPage].id)
                                }
                            }

                            HorizontalPager(
                                state = pagerState,
                                pageSpacing = 12.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) { page ->
                                val coach = coachTrainerOptions[page]
                                val coachName = stringResource(coach.nameRes)
                                val photo = coach.avatar  // use portrait photo, not exercise shot
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(340.dp)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                ) {
                                    Image(
                                        painter = painterResource(photo),
                                        contentDescription = coachName,
                                        contentScale = ContentScale.Crop,
                                        alignment = Alignment.TopCenter,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Dark gradient overlay at the bottom
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                                )
                                            )
                                            .padding(16.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = coachName,
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = stringResource(Res.string.coach_trainer_avatar_hint),
                                                color = Color.White.copy(alpha = 0.80f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }

                            // Page indicator dots
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                coachTrainerOptions.indices.forEach { i ->
                                    val isSelected = pagerState.currentPage == i
                                    Box(
                                        Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(if (isSelected) 10.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) AiPalette.Primary
                                                else Color.Gray.copy(alpha = 0.35f)
                                            )
                                    )
                                }
                            }
                        }

                        4 -> {
                            StepTitle(stringResource(Res.string.label_training_mode))
                            Text(
                                text = stringResource(Res.string.training_mode_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onCard.copy(alpha = 0.72f)
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
                            StepTitle(stringResource(Res.string.label_training_focus))
                            Text(
                                text = stringResource(Res.string.training_focus_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onCard.copy(alpha = 0.72f)
                            )
                            Spacer(Modifier.height(4.dp))
                            val focusOptions = listOf(
                                TRAINING_FOCUS_HYPERTROPHY to Triple(
                                    stringResource(Res.string.training_focus_hypertrophy),
                                    stringResource(Res.string.training_focus_hypertrophy_reps),
                                    stringResource(Res.string.training_focus_hypertrophy_desc)
                                ),
                                TRAINING_FOCUS_STRENGTH to Triple(
                                    stringResource(Res.string.training_focus_strength),
                                    stringResource(Res.string.training_focus_strength_reps),
                                    stringResource(Res.string.training_focus_strength_desc)
                                ),
                                TRAINING_FOCUS_GENERAL to Triple(
                                    stringResource(Res.string.training_focus_general),
                                    stringResource(Res.string.training_focus_general_reps),
                                    stringResource(Res.string.training_focus_general_desc)
                                ),
                                TRAINING_FOCUS_FAT_LOSS to Triple(
                                    stringResource(Res.string.training_focus_fat_loss),
                                    stringResource(Res.string.training_focus_fat_loss_reps),
                                    stringResource(Res.string.training_focus_fat_loss_desc)
                                )
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                focusOptions.forEach { (focus, labels) ->
                                    val (name, reps, desc) = labels
                                    val selected = state.trainingFocus == focus
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { presenter.update { it.copy(trainingFocus = focus) } },
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
                                                name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) AiPalette.Primary else onCard
                                            )
                                            Text(
                                                reps,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (selected) AiPalette.Primary.copy(alpha = 0.8f) else onCard.copy(alpha = 0.55f)
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

                        6 -> {
                            StepTitle(stringResource(Res.string.label_session_duration))
                            Text(
                                text = stringResource(Res.string.session_duration_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onCard.copy(alpha = 0.72f)
                            )
                            Spacer(Modifier.height(4.dp))
                            val durationOptions = listOf(
                                30 to (stringResource(Res.string.session_30min) to stringResource(Res.string.session_30min_desc)),
                                45 to (stringResource(Res.string.session_45min) to stringResource(Res.string.session_45min_desc)),
                                60 to (stringResource(Res.string.session_60min) to stringResource(Res.string.session_60min_desc)),
                                90 to (stringResource(Res.string.session_90min) to stringResource(Res.string.session_90min_desc))
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                durationOptions.forEach { (mins, labels) ->
                                    val (label, desc) = labels
                                    val selected = state.sessionDurationMins == mins
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { presenter.update { it.copy(sessionDurationMins = mins) } },
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

                        7 -> {
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

                        8 -> {
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

                        9 -> {
                            StepTitle(stringResource(Res.string.label_injuries))
                            Text(
                                text = stringResource(Res.string.injuries_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onCard.copy(alpha = 0.72f)
                            )
                            // Quick-pick common injuries
                            val commonInjuries = listOf(
                                stringResource(Res.string.injury_knee),
                                stringResource(Res.string.injury_back),
                                stringResource(Res.string.injury_shoulder),
                                stringResource(Res.string.injury_wrist),
                                stringResource(Res.string.injury_elbow),
                                stringResource(Res.string.injury_hip),
                                stringResource(Res.string.injury_ankle),
                                stringResource(Res.string.injury_neck)
                            )
                            val selectedInjuries = state.injuries.split(",")
                                .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                commonInjuries.forEach { injury ->
                                    val selected = injury in selectedInjuries
                                    ModernToggleChip(
                                        label = injury,
                                        selected = selected,
                                        onClick = {
                                            presenter.update { st ->
                                                val cur = st.injuries.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                                                if (selected) cur.remove(injury) else cur.add(injury)
                                                st.copy(injuries = cur.joinToString(", "))
                                            }
                                        }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = state.injuries,
                                onValueChange = { presenter.update { st -> st.copy(injuries = it) } },
                                label = { Text(stringResource(Res.string.label_injuries_manual)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = inputColors
                            )
                        }

                        10 -> {
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

                        11 -> {
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

                        12 -> {
                            StepTitle(stringResource(Res.string.label_split_preference))
                            Text(
                                text = stringResource(Res.string.split_preference_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onCard.copy(alpha = 0.72f)
                            )
                            Spacer(Modifier.height(4.dp))
                            val splitOptions = listOf(
                                SplitPreference.AUTO to (stringResource(Res.string.split_auto) to stringResource(Res.string.split_auto_desc)),
                                SplitPreference.FULL_BODY to (stringResource(Res.string.split_full_body) to stringResource(Res.string.split_full_body_desc)),
                                SplitPreference.UPPER_LOWER to (stringResource(Res.string.split_upper_lower) to stringResource(Res.string.split_upper_lower_desc)),
                                SplitPreference.PPL to (stringResource(Res.string.split_ppl) to stringResource(Res.string.split_ppl_desc))
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                splitOptions.forEach { (pref, labels) ->
                                    val (label, desc) = labels
                                    val selected = state.splitPreference == pref
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { presenter.update { it.copy(splitPreference = pref) } },
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

            Spacer(Modifier.height(32.dp))
        }

        if (state.saving) {
            SavingOverlay(savingStep = state.savingStep)
        }
    }
}

@Composable
private fun SavingOverlay(savingStep: Int) {
    val subMessages = listOf(
        stringResource(Res.string.saving_sub_analyze),
        stringResource(Res.string.saving_sub_workouts),
        stringResource(Res.string.saving_sub_exercises),
        stringResource(Res.string.saving_sub_nutrition),
        stringResource(Res.string.saving_sub_sleep),
        stringResource(Res.string.saving_sub_finishing)
    )
    var subMsgIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(savingStep) {
        if (savingStep >= 1) {
            while (true) {
                delay(2000)
                subMsgIdx = (subMsgIdx + 1) % subMessages.size
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(text = "🤖", fontSize = 52.sp)

                Text(
                    text = stringResource(Res.string.saving_overlay_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onCard,
                    textAlign = TextAlign.Center
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SavingStepRow(
                        done = savingStep >= 1,
                        active = savingStep == 0,
                        text = stringResource(Res.string.saving_step_profile)
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SavingStepRow(
                            done = false,
                            active = savingStep >= 1,
                            text = stringResource(Res.string.saving_step_plan)
                        )
                        if (savingStep >= 1) {
                            AnimatedContent(
                                targetState = subMsgIdx,
                                modifier = Modifier.padding(start = 36.dp),
                                transitionSpec = { fadeIn() togetherWith fadeOut() }
                            ) { idx ->
                                Text(
                                    text = subMessages[idx % subMessages.size],
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onCard.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    color = AiPalette.Primary,
                    trackColor = AiPalette.Primary.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
private fun SavingStepRow(done: Boolean, active: Boolean, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            done -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
            active -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = AiPalette.Primary
            )
            else -> Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(1.5.dp, onCard.copy(alpha = 0.2f), CircleShape)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active || done) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active || done) onCard else onCard.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = onCard,
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
            labelColor = onCard.copy(alpha = 0.8f)
        ),
        border = null,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
fun CoachChoiceCard(
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
                color = onCard,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(Res.string.coach_trainer_avatar_hint),
                color = onCard.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ModernToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) AiPalette.Primary else AiPalette.SurfaceLight
    val textColor = if (selected) AiPalette.OnGradient else onCard.copy(alpha = 0.8f)
    
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}
