package com.wpspasswordmanager.ui.compose.assistant

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wpspasswordmanager.ui.compose.AppTheme

/**
 * 悬浮窗里的 Compose 容器。
 * WindowManager 直接挂 Compose 需要一个 LifecycleOwner（否则 AbstractComposeView 会抛
 * "ViewTreeLifecycleOwner not found"），这里由 Service 侧的 [ServiceOwner] 提供。
 */
class ComposeOverlayHost(context: Context) : AbstractComposeView(context) {

    var content: @Composable () -> Unit = {}

    override val shouldCreateCompositionOnAttachedToWindow: Boolean = true

    @Composable
    override fun Content() {
        AppTheme { content() }
    }
}

/** 给悬浮窗 Compose 用的最小 Lifecycle / SavedState / ViewModelStore 所有者 */
class ServiceOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun attach(view: ComposeOverlayHost) {
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
