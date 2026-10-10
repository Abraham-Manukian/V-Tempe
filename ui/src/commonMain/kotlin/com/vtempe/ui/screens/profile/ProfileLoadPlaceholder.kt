package com.vtempe.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.ui.*
import com.vtempe.ui.presenter.ProfileLoad
import org.jetbrains.compose.resources.stringResource

/** Shown instead of a profile form while it is loading, when reading it failed, or when there is none. */
@Composable
internal fun ProfileLoadPlaceholder(load: ProfileLoad, onRetry: () -> Unit) {
    BrandScreen(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (load) {
                ProfileLoad.LOADING, ProfileLoad.READY ->
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                ProfileLoad.FAILED, ProfileLoad.MISSING -> {
                    Text(
                        stringResource(if (load == ProfileLoad.FAILED) Res.string.profile_load_failed else Res.string.profile_missing),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.common_retry)) }
                }
            }
        }
    }
}
