@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.vtempe.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.exercise.BrowseExercise
import com.vtempe.shared.domain.exercise.ExerciseBrowseCatalog
import com.vtempe.shared.domain.exercise.ExerciseVisualFamily
import com.vtempe.shared.domain.exercise.TrainMode
import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
import com.vtempe.ui.Res
import com.vtempe.ui.*
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExerciseLibraryScreen(
    coachTrainerId: String = CoachTrainerIds.DEFAULT,
) {
    val localeTag = Locale.current.language
    var selectedMode by remember { mutableStateOf<TrainMode?>(null) }
    var detailExercise by remember { mutableStateOf<BrowseExercise?>(null) }

    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    val filtered = ExerciseBrowseCatalog.byMode(selectedMode)
    val grouped = filtered.groupBy { it.muscle }
        .toList()
        .sortedBy { it.first.ordinal }

    BrandScreen(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topBarHeight + 12.dp,
                bottom = bottomBarHeight + 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeFilterChip(
                        label = stringResource(Res.string.exlib_all),
                        selected = selectedMode == null,
                        onClick = { selectedMode = null },
                        modifier = Modifier.weight(1f)
                    )
                    ModeFilterChip(
                        label = stringResource(Res.string.exlib_gym),
                        selected = selectedMode == TrainMode.GYM,
                        onClick = { selectedMode = TrainMode.GYM },
                        modifier = Modifier.weight(1f)
                    )
                    ModeFilterChip(
                        label = stringResource(Res.string.exlib_home),
                        selected = selectedMode == TrainMode.HOME,
                        onClick = { selectedMode = TrainMode.HOME },
                        modifier = Modifier.weight(1f)
                    )
                    ModeFilterChip(
                        label = stringResource(Res.string.exlib_outdoor),
                        selected = selectedMode == TrainMode.OUTDOOR,
                        onClick = { selectedMode = TrainMode.OUTDOOR },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Text(
                    stringResource(Res.string.exlib_count).kmpFormat(filtered.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            grouped.forEach { (muscle, exercises) ->
                item(key = "header_${muscle.name}") {
                    Text(
                        text = if (localeTag.startsWith("ru")) muscle.ru else muscle.en,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                items(exercises.size, key = { "${muscle.name}_$it" }) { idx ->
                    ExerciseRow(
                        exercise = exercises[idx],
                        coachTrainerId = coachTrainerId,
                        localeTag = localeTag,
                        onClick = { detailExercise = exercises[idx] }
                    )
                }
            }
        }
    }

    detailExercise?.let { ex ->
        ExerciseDetailSheet(
            exercise = ex,
            coachTrainerId = coachTrainerId,
            localeTag = localeTag,
            onDismiss = { detailExercise = null }
        )
    }
}

// ── Detail sheet ──────────────────────────────────────────────────────────────

@Composable
private fun ExerciseDetailSheet(
    exercise: BrowseExercise,
    coachTrainerId: String,
    localeTag: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val guide = exerciseGuide(exercise.id, coachTrainerId)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Large illustration
            Image(
                painter = painterResource(guide.illustration),
                contentDescription = exercise.name(localeTag),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Name + badges
                Text(
                    text = exercise.name(localeTag),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DifficultyBadge(exercise.difficulty)
                    exercise.modes.sortedBy { it.ordinal }.forEach { ModeTag(it) }
                }

                // Description
                if (guide.description.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = guide.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }

                // Focus
                if (guide.focus.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = AiPalette.Primary.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = guide.focus.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = AiPalette.Primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Steps
                if (guide.steps.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.exlib_detail_steps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))
                    guide.steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier.padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(AiPalette.Primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AiPalette.Primary
                                )
                            }
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Key cue
                if (guide.cue.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = AiPalette.DeepAccent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(text = "💡", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = guide.cue,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Row card ─────────────────────────────────────────────────────────────────

@Composable
private fun ExerciseRow(
    exercise: BrowseExercise,
    coachTrainerId: String,
    localeTag: String,
    onClick: () -> Unit,
) {
    val illustration = coachExerciseIllustration(
        coachTrainerId,
        exercise.id,
        illustrationFor(ExerciseVisualFamily.GENERIC)
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(illustration),
                contentDescription = exercise.name(localeTag),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = exercise.name(localeTag),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    exercise.modes.sortedBy { it.ordinal }.forEach { mode ->
                        ModeTag(mode)
                    }
                }
            }
            DifficultyBadge(exercise.difficulty)
        }
    }
}

// ── Shared sub-composables ────────────────────────────────────────────────────

@Composable
private fun ModeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AiPalette.DeepAccent else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) AiPalette.OnDeepAccent else Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun ModeTag(mode: TrainMode) {
    val label = when (mode) {
        TrainMode.GYM -> stringResource(Res.string.exlib_gym)
        TrainMode.HOME -> stringResource(Res.string.exlib_home)
        TrainMode.OUTDOOR -> stringResource(Res.string.exlib_outdoor)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AiPalette.Primary.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AiPalette.Primary
        )
    }
}

@Composable
private fun DifficultyBadge(difficulty: Int) {
    val (color, label) = when (difficulty) {
        1 -> AiPalette.Primary to stringResource(Res.string.exlib_diff_1)
        2 -> AiPalette.Primary to stringResource(Res.string.exlib_diff_2)
        3 -> AiPalette.DeepAccent to stringResource(Res.string.exlib_diff_3)
        4 -> Color(0xFFE08A00) to stringResource(Res.string.exlib_diff_4)
        else -> Color(0xFFD13438) to stringResource(Res.string.exlib_diff_5)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
