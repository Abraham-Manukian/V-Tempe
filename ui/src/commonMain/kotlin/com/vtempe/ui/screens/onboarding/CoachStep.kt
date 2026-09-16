@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.vtempe.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.*
import com.vtempe.ui.coach.coachTrainerOptions
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.presenter.OnboardingState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CoachStep(state: OnboardingState, presenter: OnboardingPresenter) {
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
