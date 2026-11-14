package com.dhananjay.livecast.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

private class ScopedViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

@Composable
fun rememberScopedViewModelStoreOwner(vararg keys: Any?): ViewModelStoreOwner {
    val owner = remember(keys = keys) { ScopedViewModelStoreOwner() }
    DisposableEffect(owner) {
        onDispose {
            owner.viewModelStore.clear()
        }
    }
    return owner
}
