@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens

import com.vtempe.ui.*
import com.vtempe.ui.presenter.SettingsPresenter
import com.vtempe.ui.presenter.TRAINING_MODE_GYM
import com.vtempe.ui.presenter.TRAINING_MODE_HOME
import com.vtempe.ui.presenter.TRAINING_MODE_OUTDOOR
import com.vtempe.ui.presenter.TRAINING_MODE_MIXED

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.*
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

    var edit by remember { mutableStateOf(EditProfileState()) }
    LaunchedEffect(profile) { profile?.let { edit = it.toEditState() } }

    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    if (profile == null) {
        BrandScreen(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.loading), color = Color.White)
            }
        }
        return
    }

    BrandScreen(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(topBarHeight + 16.dp))
            BasicInfoSection(edit, onUpdate = { edit = it })
            TrainingFocusSection(edit, onUpdate = { edit = it })
            SessionDurationSection(edit, onUpdate = { edit = it })
            SplitPreferenceSection(edit, onUpdate = { edit = it })
            ExperienceLifestyleSection(edit, onUpdate = { edit = it })
            DietSection(edit, onUpdate = { edit = it })
            InjuriesSection(edit, onUpdate = { edit = it })
            CoachSection(edit, onUpdate = { edit = it })
            Button(
                onClick = {
                    if (edit.isValid) {
                        presenter.save(edit.applyTo(profile))
                        presenter.refresh()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiPalette.DeepAccent,
                    contentColor = AiPalette.OnDeepAccent
                ),
                enabled = !state.saving && edit.isValid
            ) {
                Text(stringResource(Res.string.edit_profile_save), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(bottomBarHeight + 32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sections
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BasicInfoSection(edit: EditProfileState, onUpdate: (EditProfileState) -> Unit) {
    ProfileCard {
        OutlinedTextField(
            value = edit.age,
            onValueChange = { onUpdate(edit.copy(age = it.filter(Char::isDigit).take(3))) },
            label = { Text(stringResource(Res.string.label_age)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = edit.heightCm,
            onValueChange = { onUpdate(edit.copy(heightCm = it.filter(Char::isDigit).take(3))) },
            label = { Text(stringResource(Res.string.label_height_cm)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = edit.weightKg,
            onValueChange = { onUpdate(edit.copy(weightKg = it.filter { c -> c.isDigit() || c == '.' }.take(5))) },
            label = { Text(stringResource(Res.string.label_weight_kg)) },
            modifier = Modifier.fillMaxWidth()
        )
        SectionHeading(stringResource(Res.string.label_goal))
        Goal.entries.forEach { g ->
            RadioRow(
                selected = edit.goal == g,
                label = when (g) {
                    Goal.LOSE_FAT    -> stringResource(Res.string.goal_lose_fat)
                    Goal.MAINTAIN    -> stringResource(Res.string.goal_maintain)
                    Goal.GAIN_MUSCLE -> stringResource(Res.string.goal_gain_muscle)
                },
                onClick = { onUpdate(edit.copy(goal = g)) }
            )
        }
        SectionHeading(stringResource(Res.string.label_training_mode))
        listOf(
            TRAINING_MODE_GYM     to stringResource(Res.string.training_mode_gym),
            TRAINING_MODE_HOME    to stringResource(Res.string.training_mode_home),
            TRAINING_MODE_OUTDOOR to stringResource(Res.string.training_mode_outdoor),
            TRAINING_MODE_MIXED   to stringResource(Res.string.training_mode_mixed)
        ).forEach { (mode, label) ->
            RadioRow(
                selected = edit.trainingMode == mode,
                label = label,
                onClick = { onUpdate(edit.copy(trainingMode = mode)) }
            )
        }
    }
}

@Composable
private fun TrainingFocusSection(edit: EditProfileState, onUpdate: (EditProfileState) -> Unit) {
    val options = listOf(
        TrainingFocus.STRENGTH    to Triple(stringResource(Res.string.training_focus_strength),    stringResource(Res.string.training_focus_strength_reps),    stringResource(Res.string.training_focus_strength_desc)),
        TrainingFocus.HYPERTROPHY to Triple(stringResource(Res.string.training_focus_hypertrophy), stringResource(Res.string.training_focus_hypertrophy_reps), stringResource(Res.string.training_focus_hypertrophy_desc)),
        TrainingFocus.GENERAL     to Triple(stringResource(Res.string.training_focus_general),     stringResource(Res.string.training_focus_general_reps),     stringResource(Res.string.training_focus_general_desc)),
        TrainingFocus.FAT_LOSS    to Triple(stringResource(Res.string.training_focus_fat_loss),    stringResource(Res.string.training_focus_fat_loss_reps),    stringResource(Res.string.training_focus_fat_loss_desc)),
    )
    ProfileCard {
        SectionHeading(stringResource(Res.string.label_training_focus))
        HintText(stringResource(Res.string.training_focus_hint))
        options.forEach { (focus, labels) ->
            val (name, reps, desc) = labels
            SelectableCard(
                selected = edit.trainingFocus == focus,
                onClick = { onUpdate(edit.copy(trainingFocus = focus)) }
            ) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(reps, style = MaterialTheme.typography.labelSmall, color = AiPalette.Primary)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1C1C28).copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun SessionDurationSection(edit: EditProfileState, onUpdate: (EditProfileState) -> Unit) {
    val options = listOf(
        30 to (stringResource(Res.string.session_30min) to stringResource(Res.string.session_30min_desc)),
        45 to (stringResource(Res.string.session_45min) to stringResource(Res.string.session_45min_desc)),
        60 to (stringResource(Res.string.session_60min) to stringResource(Res.string.session_60min_desc)),
        90 to (stringResource(Res.string.session_90min) to stringResource(Res.string.session_90min_desc)),
    )
    ProfileCard {
        SectionHeading(stringResource(Res.string.label_session_duration))
        HintText(stringResource(Res.string.session_duration_hint))
        options.forEach { (mins, labels) ->
            val (label, desc) = labels
            SelectableCard(
                selected = edit.sessionDurationMins == mins,
                onClick = { onUpdate(edit.copy(sessionDurationMins = mins)) }
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1C1C28).copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun SplitPreferenceSection(edit: EditProfileState, onUpdate: (EditProfileState) -> Unit) {
    val options = listOf(
        SplitPreference.AUTO        to (stringResource(Res.string.split_auto)        to stringResource(Res.string.split_auto_desc)),
        SplitPreference.FULL_BODY   to (stringResource(Res.string.split_full_body)   to stringResource(Res.string.split_full_body_desc)),
        SplitPreference.UPPER_LOWER to (stringResource(Res.string.split_upper_lower) to stringResource(Res.string.split_upper_lower_desc)),
        SplitPreference.PPL         to (stringResource(Res.string.split_ppl)         to stringResource(Res.string.split_ppl_desc)),
    )
    ProfileCard {
        SectionHeading(stringResource(Res.string.label_split_preference))
        HintText(stringResource(Res.string.split_preference_hint))
        options.forEach { (pref, labels) ->
            val (label, desc) = labels
            SelectableCard(
                selected = edit.splitPreference == pref,
                onClick = { onUpdate(edit.copy(splitPreference = pref)) }
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1C1C28).copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ExperienceLifestyleSection(edit: EditProfileState, onUpdate: (EditProfileState) -> Unit) {
    val expLabel = when (edit.experienceLevel) {
        1    -> stringResource(Res.string.experience_level_1)
        2    -> stringResource(Res.string.experience_level_2)
        3    -> stringResource(Res.string.experience_level_3)
        4    -> stringResource(Res.string.experience_level_4)
        else -> stringResource(Res.string.experience_level_5)
    }
    val lifestyleOptions = listOf(
        LifestyleActivity.SEDENTARY   to (stringResource(Res.string.lifestyle_sedentary)   to stringResource(Res.string.lifestyle_sedentary_desc)),
        LifestyleActivity.LIGHT       to (stringResource(Res.string.lifestyle_light)       to stringResource(Res.string.lifestyle_light_desc)),
        LifestyleActivity.ACTIVE      to (stringResource(Res.string.lifestyle_active)      to stringResource(Res.string.lifestyle_active_desc)),
        LifestyleActivity.VERY_ACTIVE to (stringResource(Res.string.lifestyle_very_active) to stringResource(Res.string.lifestyle_very_active_desc)),
    )
    ProfileCard {
        SectionHeading(stringResource(Res.string.label_experience).kmpFormat(edit.experienceLevel))
        Text(expLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AiPalette.Primary)
        Slider(
            value = edit.experienceLevel.toFloat(),
            onValueChange = { onUpdate(edit.copy(experienceLevel = it.toInt().coerceIn(1, 5))) },
            valueRange = 1f..5f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = AiPalette.Primary,
                activeTrackColor = AiPalette.Primary,
                inactiveTrackColor = AiPalette.Primary.copy(alpha = 0.2f)
            )
        )
        SectionHeading(stringResource(Res.string.label_lifestyle))
        HintText(stringResource(Res.string.lifestyle_hint))
        lifestyleOptions.forEach { (activity, labels) ->
            val (label, desc) = labels
            SelectableCard(
                selected = edit.lifestyleActivity == activity,
                onClick = { onUpdate(edit.copy(lifestyleActivity = activity)) }
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1C1C28).copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun DietSection(edit: EditProfileState, onUpdate: (EditProfileState) -> Unit) {
    ProfileCard {
        SectionHeading(stringResource(Res.string.edit_profile_diet_section))
        HintText(stringResource(Res.string.edit_profile_diet_hint))
        OutlinedTextField(
            value = edit.dietaryPrefs,
            onValueChange = { onUpdate(edit.copy(dietaryPrefs = it)) },
            label = { Text(stringResource(Res.string.label_dietary_prefs)) },
            placeholder = { Text(stringResource(Res.string.placeholder_dietary_prefs)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        OutlinedTextField(
            value = edit.allergies,
            onValueChange = { onUpdate(edit.copy(allergies = it)) },
            label = { Text(stringResource(Res.string.label_allergies)) },
            placeholder = { Text(stringResource(Res.string.placeholder_allergies)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
    }
}

@Composable
private fun InjuriesSection(edit: EditProfileState, onUpdate: (EditProfileState) -> Unit) {
    val commonInjuries = listOf(
        stringResource(Res.string.injury_knee),
        stringResource(Res.string.injury_back),
        stringResource(Res.string.injury_shoulder),
        stringResource(Res.string.injury_wrist),
        stringResource(Res.string.injury_elbow),
        stringResource(Res.string.injury_hip),
        stringResource(Res.string.injury_ankle),
        stringResource(Res.string.injury_neck),
    )
    val selected = remember(edit.injuries) {
        edit.injuries.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    ProfileCard {
        SectionHeading(stringResource(Res.string.label_injuries))
        HintText(stringResource(Res.string.injuries_hint))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            commonInjuries.forEach { injury ->
                val isSelected = injury in selected
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val cur = selected.toMutableSet()
                        if (isSelected) cur.remove(injury) else cur.add(injury)
                        onUpdate(edit.copy(injuries = cur.joinToString(", ")))
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
            value = edit.injuries,
            onValueChange = { onUpdate(edit.copy(injuries = it)) },
            label = { Text(stringResource(Res.string.label_injuries_manual)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
    }
}

@Composable
private fun CoachSection(edit: EditProfileState, onUpdate: (EditProfileState) -> Unit) {
    ProfileCard {
        SectionHeading(stringResource(Res.string.label_coach_trainer))
        coachTrainerOptions.forEach { coach ->
            CoachChoiceCard(
                coach = coach,
                selected = edit.coachTrainerId == coach.id,
                onClick = { onUpdate(edit.copy(coachTrainerId = coach.id)) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Primitives
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SelectableCard(selected: Boolean, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) AiPalette.Primary.copy(alpha = 0.12f) else Color.White
        ),
        border = if (selected) BorderStroke(2.dp, AiPalette.Primary)
                 else BorderStroke(1.dp, AiPalette.Outline.copy(alpha = 0.25f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun RadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = Color(0xFF1C1C28))
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, color = Color(0xFF1C1C28), fontWeight = FontWeight.SemiBold)
}

@Composable
private fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1C1C28).copy(alpha = 0.6f))
}
