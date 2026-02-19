@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.vtempe.ui.screens
import com.vtempe.ui.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.core.designsystem.theme.AppThemeColor
import com.vtempe.shared.domain.model.Profile
import com.vtempe.ui.platform.SettingsPlatformActions
import com.vtempe.ui.platform.rememberSettingsPlatformActions
import com.vtempe.ui.util.kmpFormat
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onEditProfile: () -> Unit = {},
    presenter: SettingsPresenter = rememberSettingsPresenter(),
    platformActions: SettingsPlatformActions = rememberSettingsPlatformActions()
) {
    val state by presenter.state.collectAsState()
    val profile = state.profile
    
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    var showColors by remember { mutableStateOf(false) }

    if (profile == null) {
        LaunchedEffect(Unit) { presenter.refresh() }
        BrandScreen(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
            }
        }
        return
    }

    val contentColor = MaterialTheme.colorScheme.onSurface
    BrandScreen(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(topBarHeight + 16.dp))
            
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { it / 5 }, animationSpec = tween(300))
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsCardColors(),
                    elevation = settingsCardElevation(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "Avatar",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(Res.string.settings_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                                Text(
                                    stringResource(Res.string.settings_age).kmpFormat(profile.age),
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                                Text(
                                    stringResource(Res.string.settings_height_weight).kmpFormat(
                                        profile.heightCm,
                                        profile.weightKg
                                    ),
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ProfileStatsGrid(profile = profile)
                        OutlinedButton(
                            onClick = onEditProfile,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) { Text(stringResource(Res.string.settings_edit_profile)) }
                    }
                }
            }

            PreferenceCard(
                title = "\u0426\u0432\u0435\u0442\u043e\u0432\u0430\u044f \u0442\u0435\u043c\u0430",
                onClick = { showColors = !showColors },
                trailing = {
                    val rotation by animateFloatAsState(if (showColors) 180f else 0f)
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.rotate(rotation)
                    )
                }
            ) {
                AnimatedVisibility(visible = showColors) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppThemeColor.entries.forEach { themeColor ->
                                val isSelected = AiPalette.CurrentPrimary == themeColor.primary
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(themeColor.primary)
                                        .clickable {
                                            AiPalette.CurrentPrimary = themeColor.primary
                                            AiPalette.CurrentDeep = themeColor.deep
                                            AiPalette.CurrentLight = themeColor.light
                                        }
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) AiPalette.OnGradient else Color.Black.copy(alpha = 0.1f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = AiPalette.OnGradient,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (!showColors) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(AiPalette.Primary)
                        )
                        Text("\u0412\u044b\u0431\u0440\u0430\u0442\u044c \u0434\u0440\u0443\u0433\u043e\u0439 \u0446\u0432\u0435\u0442", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            PreferenceCard(title = stringResource(Res.string.settings_units_title)) {
                OptionRow {
                    OptionButton(stringResource(Res.string.settings_units_metric)) { presenter.setUnits("metric") }
                    OptionButton(stringResource(Res.string.settings_units_imperial)) { presenter.setUnits("imperial") }
                }
            }

            PreferenceCard(title = stringResource(Res.string.settings_language_title)) {
                OptionRow {
                    OptionButton(stringResource(Res.string.settings_language_ru)) {
                        presenter.setLanguage("ru")
                        platformActions.restartApp()
                    }
                    OptionButton(stringResource(Res.string.settings_language_system)) {
                        presenter.setLanguage(null)
                        platformActions.restartApp()
                    }
                }
            }

            if (state.saving) {
                Text(stringResource(Res.string.settings_saving), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
            }
            Button(
                onClick = { presenter.reset { platformActions.restartApp() } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiPalette.DeepAccent,
                    contentColor = AiPalette.OnDeepAccent
                ),
                shape = MaterialTheme.shapes.large,
                enabled = !state.saving
            ) {
                Text("\u0417\u0430\u043d\u043e\u0432\u043e \u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0430\u0446\u0438\u044f", fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(bottomBarHeight + 16.dp))
        }
    }
}

@Composable
private fun PreferenceCard(
    title: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = settingsCardColors(),
        elevation = settingsCardElevation(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                trailing?.invoke(this)
            }
            content()
        }
    }
}

@Composable
private fun OptionRow(content: @Composable RowScope.() -> Unit) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun OptionButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = AiPalette.Primary.copy(alpha = 0.18f),
            contentColor = AiPalette.DeepAccent
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun settingsCardColors() = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))

@Composable
private fun settingsCardElevation() = CardDefaults.cardElevation(defaultElevation = 6.dp)

@Composable
private fun ProfileStatsGrid(profile: Profile) {
    val activeDays = profile.weeklySchedule.count { it.value }
    val bmi = profile.weightKg / ((profile.heightCm / 100.0) * (profile.heightCm / 100.0))
    val bmiLabel = ((bmi * 10).roundToInt() / 10.0).toString()
    val goalLabel = when (profile.goal) {
        com.vtempe.shared.domain.model.Goal.LOSE_FAT -> "Fat loss"
        com.vtempe.shared.domain.model.Goal.GAIN_MUSCLE -> "Muscle gain"
        com.vtempe.shared.domain.model.Goal.MAINTAIN -> "Maintain"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileStatPill(
                icon = Icons.Filled.Scale,
                label = stringResource(Res.string.settings_weight_label),
                value = stringResource(Res.string.settings_weight_value).kmpFormat(profile.weightKg),
                modifier = Modifier.weight(1f)
            )
            ProfileStatPill(
                icon = Icons.Filled.Straighten,
                label = stringResource(Res.string.settings_height_label),
                value = stringResource(Res.string.settings_height_value).kmpFormat(profile.heightCm),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileStatPill(
                icon = Icons.Filled.FitnessCenter,
                label = stringResource(Res.string.settings_experience_label),
                value = stringResource(Res.string.settings_experience_value).kmpFormat(profile.experienceLevel),
                modifier = Modifier.weight(1f)
            )
            ProfileStatPill(
                icon = Icons.Filled.Schedule,
                label = stringResource(Res.string.settings_days_label),
                value = activeDays.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileStatPill(
                icon = Icons.Filled.MonitorHeart,
                label = stringResource(Res.string.settings_bmi_label),
                value = bmiLabel,
                modifier = Modifier.weight(1f)
            )
            ProfileStatPill(
                icon = Icons.Filled.Wallet,
                label = stringResource(Res.string.settings_goal_label),
                value = goalLabel,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProfileStatPill(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.18f)),
        border = BorderStroke(1.dp, Color(0xFFD9DBE0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = AiPalette.Primary.copy(alpha = 0.15f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AiPalette.DeepAccent,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF4B4B61))
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1C28)
                )
            }
        }
    }
}
