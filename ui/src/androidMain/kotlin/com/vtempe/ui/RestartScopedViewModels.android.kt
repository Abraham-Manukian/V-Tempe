package com.vtempe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Screen ViewModels would otherwise live in the Activity's store and survive an in-app restart.
 * The store is kept in an Activity-scoped holder, so it still survives configuration changes.
 */
@Composable
internal actual fun RestartScopedViewModels(generation: Int, content: @Composable () -> Unit) {
    val holder = viewModel<GenerationViewModelStores>()
    val store = holder.storeFor(generation)
    val owner = remember(store) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

internal class GenerationViewModelStores : ViewModel() {
    private var generation: Int? = null
    private var store = ViewModelStore()

    fun storeFor(requested: Int): ViewModelStore {
        if (generation != null && generation != requested) {
            store.clear()
            store = ViewModelStore()
        }
        generation = requested
        return store
    }

    override fun onCleared() = store.clear()
}
