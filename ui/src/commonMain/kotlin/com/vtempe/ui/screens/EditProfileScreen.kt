@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens
import com.vtempe.ui.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Profile
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
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
    
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    LaunchedEffect(profile) {
        profile?.let {
            age.value = it.age.toString()
            height.value = it.heightCm.toString()
            weight.value = it.weightKg.toString()
            goal.value = it.goal
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

    BrandScreen(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(topBarHeight + 16.dp))
            
            Text(
                stringResource(Res.string.edit_profile_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                    Text(stringResource(Res.string.label_goal), color = Color(0xFF1C1C28), fontWeight = FontWeight.SemiBold)
                    Goal.entries.forEach { g ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(selected = goal.value == g, onClick = { goal.value = g })
                            val label = when (g) {
                                Goal.LOSE_FAT -> stringResource(Res.string.goal_lose_fat)
                                Goal.MAINTAIN -> stringResource(Res.string.goal_maintain)
                                Goal.GAIN_MUSCLE -> stringResource(Res.string.goal_gain_muscle)
                            }
                            Text(label, color = Color(0xFF1C1C28))
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val ageInt = age.value.toIntOrNull()
                    val heightInt = height.value.toIntOrNull()
                    val weightDouble = weight.value.toDoubleOrNull()
                    if (ageInt != null && heightInt != null && weightDouble != null) {
                        val updated: Profile = profile.copy(
                            age = ageInt,
                            heightCm = heightInt,
                            weightKg = weightDouble,
                            goal = goal.value
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
