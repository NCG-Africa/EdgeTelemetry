package com.nathanclair.edgetelemetrysdk.utility

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.nathanclair.edgetelemetrysdk.EdgeTelemetry
import com.nathanclair.edgetelemetrysdk.R

/**
 * Adds click tracking to any View
 */
fun View.trackClicks(description: String? = null) {
    setOnClickListener { view ->
        val attributes = mutableMapOf<String, String>()

        // Get a meaningful description
        val elementId = when {
            description != null -> description
            id != View.NO_ID -> resources.getResourceEntryName(id)
            else -> this.javaClass.simpleName
        }

        // Add view-specific details
        when (this) {
            is Button -> attributes["button.text"] = text.toString()
            is EditText -> attributes["input.hint"] = hint.toString()
        }

        // Track the interaction
        EdgeTelemetry.getInstance().trackUserInteraction(
            "click",
            elementId,
            attributes
        )

        // Call original listener if exists
        (getTag(R.id.edge_original_click_listener) as? View.OnClickListener)?.onClick(view)
    }
}

/**
 * Setup automatic UI tracking for an activity
 */
fun Activity.trackUserInteractions() {
    // Recursively find all clickable views in the activity
    fun findClickableViews(view: View): List<View> {
        val clickableViews = mutableListOf<View>()

        if (view.isClickable) {
            clickableViews.add(view)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                clickableViews.addAll(findClickableViews(view.getChildAt(i)))
            }
        }

        return clickableViews
    }

    // Get the root view
    val rootView = window.decorView.findViewById<View>(android.R.id.content)

    // Find and track all clickable views
    findClickableViews(rootView).forEach { view ->
        // Save original click listener
        val originalListener = view.getOnClickListener()
        view.setTag(R.id.edge_original_click_listener, originalListener)

        // Add tracking
        view.trackClicks()
    }
}

// Helper to get the OnClickListener (using reflection since it's private)
private fun View.getOnClickListener(): View.OnClickListener? {
    try {
        val listenerField = View::class.java.getDeclaredField("mListenerInfo")
        listenerField.isAccessible = true
        val listenerInfo = listenerField.get(this) ?: return null

        val clickListenerField = listenerInfo.javaClass.getDeclaredField("mOnClickListener")
        clickListenerField.isAccessible = true
        return clickListenerField.get(listenerInfo) as? View.OnClickListener
    } catch (e: Exception) {
        return null
    }
}