package com.example.mediaplayer

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data class Player(val videoPath: String, val videoTitle: String, val isOnline: Boolean) : NavKey
@Serializable data object Settings : NavKey
@Serializable data object Eula : NavKey
@Serializable data object Terms : NavKey
@Serializable data object About : NavKey
@Serializable data class Lock(val isSettingUp: Boolean = false) : NavKey
