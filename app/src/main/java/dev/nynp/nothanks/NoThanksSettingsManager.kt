package dev.nynp.nothanks

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BlockedView(
    val id: String,
    val name: String,
    var isBlocked: Boolean
)

object SettingsManager {
    private lateinit var sharedPreferences: android.content.SharedPreferences

    private val _isBlockingEnabled = MutableStateFlow(true)
    val isBlockingEnabled = _isBlockingEnabled.asStateFlow()

    private val _blockedViews = MutableStateFlow<List<BlockedView>>(emptyList())
    val blockedViews = _blockedViews.asStateFlow()

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        loadSettings()
        initializeBlockedViews(context)
    }

    private fun initializeBlockedViews(context: Context) {
        val initialBlockedViews = listOf(
            BlockedView("com.instagram.android:id/clips_viewer_view_pager",
                context.getString(R.string.instagram_reels_title), true),
            BlockedView("com.google.android.youtube:id/reel_player_page_container",
                context.getString(R.string.youtube_shorts_title), true)
        )

        val savedBlockedViews = sharedPreferences.getStringSet("blocked_views", null)
        _blockedViews.value = if (savedBlockedViews != null) {
            initialBlockedViews.map { view ->
                view.copy(isBlocked = savedBlockedViews.contains(view.id))
            }
        } else {
            initialBlockedViews
        }
    }

    fun setBlockingEnabled(enabled: Boolean) {
        _isBlockingEnabled.value = enabled
        sharedPreferences.edit().putBoolean("blocking_enabled", enabled).apply()
    }

    fun toggleBlockedView(id: String) {
        val updatedList = _blockedViews.value.map { view ->
            if (view.id == id) view.copy(isBlocked = !view.isBlocked) else view
        }
        _blockedViews.value = updatedList
        saveBlockedViews()
    }

    private fun loadSettings() {
        _isBlockingEnabled.value = sharedPreferences.getBoolean("blocking_enabled", true)
    }

    private fun saveBlockedViews() {
        val blockedViewIds = _blockedViews.value.filter { it.isBlocked }.map { it.id }.toSet()
        sharedPreferences.edit().putStringSet("blocked_views", blockedViewIds).apply()
    }
}