package com.example.mediaplayer.ui.main

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

class BackgroundPlayWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Always trick the system/WebView that it is visible so it keeps playing in the background
        super.onWindowVisibilityChanged(android.view.View.VISIBLE)
    }

    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        // Always trick the system/WebView that it is visible
        super.onVisibilityChanged(changedView, android.view.View.VISIBLE)
    }
}
