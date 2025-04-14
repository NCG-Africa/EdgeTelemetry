package com.nathanclair.edgetelemetrysdk

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup

class EdgeActivityLifecycleCallbacks(
    private val edgeTelemetry: EdgeTelemetry
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Create a span for activity creation
        edgeTelemetry.createSpan("activity.created")
            .setAttribute("activity.name", activity.javaClass.simpleName)
            .start()
            .end()
    }

    override fun onActivityStarted(activity: Activity) {
        edgeTelemetry.createSpan("activity.started")
            .setAttribute("activity.name", activity.javaClass.simpleName)
            .start()
            .end()
    }

    override fun onActivityResumed(activity: Activity) {
        // Track screen view when activity resumes
        edgeTelemetry.createSpan("screen.view")
            .setAttribute("screen.name", activity.javaClass.simpleName)
            .start()
            .end()

        // Auto-instrument all clickable views in the activity
        activity.window?.decorView?.findViewById<View>(android.R.id.content)?.let { rootView ->
            instrumentViews(rootView)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        edgeTelemetry.createSpan("activity.paused")
            .setAttribute("activity.name", activity.javaClass.simpleName)
            .start()
            .end()
    }

    override fun onActivityStopped(activity: Activity) {
        edgeTelemetry.createSpan("activity.stopped")
            .setAttribute("activity.name", activity.javaClass.simpleName)
            .start()
            .end()
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No need to track this usually
    }

    override fun onActivityDestroyed(activity: Activity) {
        edgeTelemetry.createSpan("activity.destroyed")
            .setAttribute("activity.name", activity.javaClass.simpleName)
            .start()
            .end()
    }

    private fun instrumentViews(view: View) {
        // If the view is clickable, instrument it
        if (view.isClickable && view.hasOnClickListeners()) {
            // Store original listener if it exists
            val originalListener = view.getOnClickListener()
            view.setTag(R.id.edge_original_click_listener, originalListener)

            // Add tracking wrapper
            view.setOnClickListener { v ->
                val elementId = if (v.id != View.NO_ID) {
                    try {
                        v.resources.getResourceEntryName(v.id)
                    } catch (e: Exception) {
                        "view-${v.id}"
                    }
                } else {
                    v.javaClass.simpleName
                }

                edgeTelemetry.trackUserInteraction(
                    "click",
                    elementId
                )

                // Call original listener
                originalListener?.onClick(v)
            }
        }

        // Recursively process child views if this is a ViewGroup
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                instrumentViews(view.getChildAt(i))
            }
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

    private fun View.hasOnClickListeners(): Boolean {
        return getOnClickListener() != null
    }
}