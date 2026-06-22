@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens
import com.vtempe.ui.*
import com.vtempe.ui.presenter.SettingsPresenter
import com.vtempe.ui.presenter.SettingsState
import com.vtempe.ui.presenter.TRAINING_MODE_GYM
import com.vtempe.ui.presenter.TRAINING_MODE_HOME
import com.vtempe.ui.presenter.TRAINING_MODE_OUTDOOR
import com.vtempe.ui.presenter.TRAINING_MODE_MIXED

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.Constraints
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.LifestyleActivity
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.SplitPreference
import com.vtempe.shared.domain.model.TrainingFocus
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditProfileScreen(
    onDone: () -> Unit = {},
    presenter: SettingsPresenter = rememberSettingsPresenter()
) {
    val state by presenter.state.collectAsState()
    val profile = state.profile

    val age = remember { mutableStateOf("") }
    val height = remember { mutableStateOf("") }
    val weight = remember { mutableStateOf("") }
    val goal = remember { mutableStateOf(Goal.MAINTAIN) }
    val trainingMode = remember { mutableStateOf(TRAINING_MODE_GYM) }
    val dietaryPrefs = remember { mutableStateOf("") }
    val allergies = remember { mutableStateOf("") }
    val coachTrainerId = remember { mutableStateOf(com.vtempe.shared.domain.model.CoachTrainerIds.DEFAULT) }
    val trainingFocus = remember { mutableStateOf(TrainingFocus.GENERAL) }
    val splitPreference = remember { mutableStateOf(SplitPreference.AUTO) }
    val experienceLevel = remember { mutableStateOf(3) }
    val lifestyleActivity = remember { mutableStateOf(LifestyleActivity.SEDENTARY) }
    val sessionDurationMins = remember { mutableStateOf(60) }
    val injuries = remember { mutableStateOf("") }

    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    LaunchedEffect(profile) {
        profile?.let {
            age.value = it.age.toString()
            height.value = it.heightCm.toString()
            weight.value = it.weightKg.toString()
            goal.value = it.goal
            trainingMode.value = when (it.trainingMode.lowercase()) {
                TRAINING_MODE_HOME -> TRAINING_MODE_HOME
                TRAINING_MODE_OUTDOOR -> TRAINING_MODE_OUTDOOR
                TRAINING_MODE_MIXED -> TRAINING_MODE_MIXED
                else -> TRAINING_MODE_GYM
            }
            dietaryPrefs.value = it.dietaryPreferences.joinToString(", ")
            allergies.value = it.allergies.joinToString(", ")
            coachTrainerId.value = it.coachTrainerId
            trainingFocus.value = it.trainingFocus
            splitPreference.value = it.splitPreference
            experienceLevel.value = it.experienceLevel.coerceIn(1, 5)
            lifestyleActivity.value = it.lifestyleActivity
            sessionDurationMins.value = it.sessionDurationMins
            injuries.value = it.constraints.injuries.joinToString(", ")
        }
    }

    if (profile == null) {
        BrandScreen(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(Res.string.loading), color = Color.White)
            }
        }
        return
    }

    val cardColors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
    val cardElevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    val headingColor = Color(0xFF1C1C28)
    val selectedBorder = BorderStroke(2.dp, AiPalette.Primary)
    val unselectedBorder = BorderStroke(1.dp, AiPalette.Outline.copy(alpha = 0.25f))

    BrandScreen(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(topBarHeight + 16.dp))

            // ── Basic info ────────────────────────────────────────────────────
            Card(colors = cardColors, elevation = cardElevation, shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = age.value,
                        onValueChange = { v -> age.value = v.filter { it.isDigit() }.take(3) },
                        label = { Text(stringResource(Res.string.label_age)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = height.value,
                        onValueChange = { v -> height.value = v.filter { it.isDigit() }.take(3) },
                        label = { Text(stringResource(Res.string.label_height_cm)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = weight.value,
                        onValueChange = { v -> weight.value = v.filter { it.isDigit() || it == '.' }.take(5) },
                        label = { Text(stringResource(Res.string.label_weight_kg)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(Res.string.label_goal), color = headingColor, fontWeight = FontWeight.SemiBold)
                    Goal.entries.forEach { g ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = goal.value == g, onClick = { goal.value = g })
                            val label = when (g) {
                                Goal.LOSE_FAT -> stringResource(Res.string.goal_lose_fat)
                                Goal.MAINTAIN -> stringResource(Res.string.goal_maintain)
                                Goal.GAIN_MUSCLE -> stringResource(Res.string.goal_gain_muscle)
                            }
                            Text(label, color = headingColor)
                        }
                    }
                    Text(stringResource(Res.string.label_training_mode), color = headingColor, fontWeight = FontWeight.SemiBold)
                    listOf(
                        TRAINING_MODE_GYM to stringResource(Res.string.training_mode_gym),
                        TRAINING_MODE_HOME to stringResource(Res.string.training_mode_home),
                        TRAINING_MODE_OUTDOOR to stringResource(Res.string.training_mode_outdoor),
                        TRAINING_MODE_MIXED to stringResource(Res.string.training_mode_mixed)
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = trainingMode.value == mode, onClick = { trainingMode.value = mode })
                            Text(label, color = headingColor)
                        }
                    }
                }
            }

            // ── Training focus ────────────────────────────────────────────────
            Card(colors = cardColors, elevation = cardElevation, shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.label_training_focus), color = headingColor, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(Res.string.training_focus_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = headingColor.copy(alpha = 0.6f)
                    )
                    val focusOptions = listOf(
                        TrainingFocus.STRENGTH to Triple(
                            stringResource(Res.string.training_focus_strength),
                            stringResource(Res.string.training_focus_strength_reps),
                            stringResource(Res.string.training_focus_strength_desc)
                        ),
                        TrainingFocus.HYPERTROPHY to Triple(
                            stringResource(Res.string.training_focus_hypertrophy),
                            stringResource(Res.string.training_focus_hypertrophy_reps),
                            stringResource(Res.string.training_focus_hypertrophy_desc)
                        ),
                        TrainingFocus.GENERAL to Triple(
                            stringResource(Res.string.training_focus_general),
                            stringResource(Res.string.training_focus_general_reps),
                            stringResource(Res.string.training_focus_general_desc)
                        ),
                        TrainingFocus.FAT_LOSS to Triple(
                            stringResource(Res.string.training_focus_fat_loss),
                            stringResource(Res.string.training_focus_fat_loss_reps),
                            stringResource(Res.string.training_focus_fat_loss_desc)
                        )
                    )
                    focusOptions.forEach { (focus, labels) ->
                        val (name, reps, desc) = labels
                        val selected = trainingFocus.value == focus
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { trainingFocus.value = focus },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) AiPalette.Primary.copy(alpha = 0.12f) else Color.White
                            ),
                            border = if (selected) selectedBorder else unselectedBorder,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = headingColor)
                                Text(reps, style = MaterialTheme.typography.labelSmall, color = AiPalette.Primary)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = headingColor.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            // ── Session duration ──────────────────────────────────────────────
            Card(colors = cardColors, elevation = cardElevation, shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.label_session_duration), color = headingColor, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(Res.string.session_duration_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = headingColor.copy(alpha = 0.6f)
                    )
                    val durationOptions = listOf(
                        30 to (stringResource(Res.string.session_30min) to stringResource(Res.string.session_30min_desc)),
                        45 to (stringResource(Res.string.session_45min) to stringResource(Res.string.session_45min_desc)),
                        60 to (stringResource(Res.string.session_60min) to stringResource(Res.string.session_60min_desc)),
                        90 to (stringResource(Res.string.session_90min) to stringResource(Res.string.session_90min_desc))
                    )
                    durationOptions.forEach { (mins, labels) ->
                        val (label, desc) = labels
                        val selected = sessionDurationMins.value == mins
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { sessionDurationMins.value = mins },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) AiPalette.Primary.copy(alpha = 0.12f) else Color.White
                            ),
                            border = if (selected) selectedBorder else unselectedBorder,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = headingColor)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = headingColor.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            // ── Split preference ──────────────────────────────────────────────
            Card(colors = cardColors, elevation = cardElevation, shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.label_split_preference), color = headingColor, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(Res.string.split_preference_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = headingColor.copy(alpha = 0.6f)
                    )
                    val splitOptions = listOf(
                        SplitPreference.AUTO to (stringResource(Res.string.split_auto) to stringResource(Res.string.split_auto_desc)),
                        SplitPreference.FULL_BODY to (stringResource(Res.string.split_full_body) to stringResource(Res.string.split_full_body_desc)),
                        SplitPreference.UPPER_LOWER to (stringResource(Res.string.split_upper_lower) to stringResource(Res.string.split_upper_lower_desc)),
                        SplitPreference.PPL to (stringResource(Res.string.split_ppl) to stringResource(Res.string.split_ppl_desc))
                    )
                    splitOptions.forEach { (pref, labels) ->
                        val (label, desc) = labels
                        val selected = splitPreference.value == pref
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { splitPreference.value = pref },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) AiPalette.Primary.copy(alpha = 0.12f) else Color.White
                            ),
                            border = if (selected) selectedBorder else unselectedBorder,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = headingColor)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = headingColor.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            // ── Experience & lifestyle ────────────────────────────────────────
            Card(colors = cardColors, elevation = cardElevation, shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val expLabel = when (experienceLevel.value) {
                        1 -> stringResource(Res.string.experience_level_1)
                        2 -> stringResource(Res.string.experience_level_2)
                        3 -> stringResource(Res.string.experience_level_3)
                        4 -> stringResource(Res.string.experience_level_4)
                        else -> stringResource(Res.string.experience_level_5)
                    }
                    Text(
                        stringResource(Res.string.label_experience).kmpFormat(experienceLevel.value),
                        color = headingColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(expLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AiPalette.Primary)
                    Slider(
                        value = experienceLevel.value.toFloat(),
                        onValueChange = { experienceLevel.value = it.toInt().coerceIn(1, 5) },
                        valueRange = 1f..5f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = AiPalette.Primary,
                            activeTrackColor = AiPalette.Primary,
                            inactiveTrackColor = AiPalette.Primary.copy(alpha = 0.2f)
                        )
                    )
                    Text(stringResource(Res.string.label_lifestyle), color = headingColor, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(Res.string.lifestyle_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = headingColor.copy(alpha = 0.6f)
                    )
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
                    lifestyleOptions.forEach { (activity, labels) ->
                        val (label, desc) = labels
                        val selected = lifestyleActivity.value == activity
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { lifestyleActivity.value = activity },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) AiPalette.Primary.copy(alpha = 0.12f) else Color.White
                            ),
                            border = if (selected) selectedBorder else unselectedBorder,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = headingColor)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = headingColor.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            // ── Diet ──────────────────────────────────────────────────────────
            Card(colors = cardColors, elevation = cardElevation, shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(Res.string.edit_profile_diet_section),
                        color = headingColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(Res.string.edit_profile_diet_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = headingColor.copy(alpha = 0.6f)
                    )
                    OutlinedTextField(
                        value = dietaryPrefs.value,
                        onValueChange = { dietaryPrefs.value = it },
                        label = { Text(stringResource(Res.string.label_dietary_prefs)) },
                        placeholder = { Text(stringResource(Res.string.placeholder_dietary_prefs)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = allergies.value,
                        onValueChange = { allergies.value = it },
                        label = { Text(stringResource(Res.string.label_allergies)) },
                        placeholder = { Text(stringResource(Res.string.placeholder_allergies)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }

            // ── Injuries ──────────────────────────────────────────────────────
            Card(colors = cardColors, elevation = cardElevation, shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.label_injuries), color = headingColor, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(Res.string.injuries_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = headingColor.copy(alpha = 0.6f)
                    )
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
                    val selectedInjuries = injuries.value.split(",")
                        .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        commonInjuries.forEach { injury ->
                            val sel = injury in selectedInjuries
                            FilterChip(
                                selected = sel,
                                onClick = {
                                    val cur = injuries.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                                    if (sel) cur.remove(injury) else cur.add(injury)
                                    injuries.value = cur.joinToString(", ")
                                },
                                label = { Text(injury) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AiPalette.Primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    OutlinedTextField(
                        value = injuries.value,
                        onValueChange = { injuries.value = it },
                        label = { Text(stringResource(Res.string.label_injuries_manual)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }

            // ── Coach / trainer ───────────────────────────────────────────────
            Card(colors = cardColors, elevation = cardElevation, shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(Res.string.label_coach_trainer),
                        color = headingColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    coachTrainerOptions.forEach { coach ->
                        CoachChoiceCard(
                            coach = coach,
                            selected = coachTrainerId.value == coach.id,
                            onClick = { coachTrainerId.value = coach.id }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val ageInt = age.value.toIntOrNull()
                    val heightInt = height.value.toIntOrNull()
                    val weightDouble = weight.value.toDoubleOrNull()
                    if (ageInt != null && heightInt != null && weightDouble != null) {
                        val parsedDietaryPrefs = dietaryPrefs.value
                            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val parsedAllergies = allergies.value
                            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val parsedInjuries = injuries.value
                            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val updated: Profile = profile.copy(
                            age = ageInt,
                            heightCm = heightInt,
                            weightKg = weightDouble,
                            goal = goal.value,
                            trainingMode = trainingMode.value,
                            dietaryPreferences = parsedDietaryPrefs,
                            allergies = parsedAllergies,
                            coachTrainerId = coachTrainerId.value,
                            trainingFocus = trainingFocus.value,
                            splitPreference = splitPreference.value,
                            experienceLevel = experienceLevel.value,
                            lifestyleActivity = lifestyleActivity.value,
                            sessionDurationMins = sessionDurationMins.value,
                            constraints = profile.constraints.copy(injuries = parsedInjuries)
                        )
                        presenter.save(updated)
                        presenter.refresh()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AiPalette.DeepAccent, contentColor = AiPalette.OnDeepAccent),
                enabled = !state.saving
            ) {
                Text(stringResource(Res.string.edit_profile_save), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(bottomBarHeight + 32.dp))
        }
    }
}
