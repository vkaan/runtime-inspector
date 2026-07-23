package com.vkaan.runtimeinspector

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import java.util.Collections

internal class LifecycleCollector : Application.ActivityLifecycleCallbacks {
    private companion object {
        const val TAG = "RuntimeInspector"
    }

    private val events = Collections.synchronizedList(mutableListOf<LifecycleEvent>())

    fun events(): List<LifecycleEvent> = events.toList()

    fun start(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        recordActivity(activity, "CREATED")
        if (activity is FragmentActivity) {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentCallbacks, true)
            activity.supportFragmentManager.addOnBackStackChangedListener(backStackListener)

        }

    }

    override fun onActivityStarted(activity: Activity) = recordActivity(activity, "STARTED")
    override fun onActivityResumed(activity: Activity) = recordActivity(activity, "RESUMED")
    override fun onActivityPaused(activity: Activity) = recordActivity(activity, "PAUSED")
    override fun onActivityStopped(activity: Activity) = recordActivity(activity, "STOPPED")
    override fun onActivityDestroyed(activity: Activity) = recordActivity(activity, "DESTROYED")
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private val fragmentCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentAttached(fm: FragmentManager, f: Fragment, ctx: Context) =
            recordFragment(f, "ATTACHED")
        override fun onFragmentCreated(fm: FragmentManager, f: Fragment, s: Bundle?) =
            recordFragment(f, "CREATED")
        override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, s: Bundle?) =
            recordFragment(f, "VIEW_CREATED")
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) =
            recordFragment(f, "STARTED")
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) =
            recordFragment(f, "RESUMED")
        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) =
            recordFragment(f, "PAUSED")
        override fun onFragmentStopped(fm: FragmentManager, f: Fragment) =
            recordFragment(f, "STOPPED")
        override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) =
            recordFragment(f, "VIEW_DESTROYED")
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) =
            recordFragment(f, "DESTROYED")
        override fun onFragmentDetached(fm: FragmentManager, f: Fragment) =
            recordFragment(f, "DETACHED")
    }

    private val backStackListener = object : FragmentManager.OnBackStackChangedListener {
        // Required by the interface, but we don't need the info-less version.
        override fun onBackStackChanged() = Unit

        // Fragment 1.4.0+: tells us WHICH fragment and whether it was a pop.
        override fun onBackStackChangeCommitted(fragment: Fragment, pop: Boolean) =
            recordFragment(fragment, if (pop) "BACKSTACK_POPPED" else "BACKSTACK_PUSHED")
    }


    // Recording

    private fun recordActivity(activity: Activity, stage: String) =
        record(
            type = LifecycleEvent.SourceType.ACTIVITY,
            name = activity::class.java.simpleName,
            stage = stage,
            instanceId = System.identityHashCode(activity),
            isChangingConfigurations =
                if (stage == "STOPPED" || stage == "DESTROYED") activity.isChangingConfigurations
                else null,
        )

    private fun recordFragment(fragment: Fragment, stage: String) =
        record(
            type = LifecycleEvent.SourceType.FRAGMENT,
            name = fragment::class.java.simpleName,
            stage = stage,
            instanceId = System.identityHashCode(fragment),
        )

    private fun record(
        type: LifecycleEvent.SourceType,
        name: String,
        stage: String,
        instanceId: Int,
        isChangingConfigurations: Boolean? = null,
    ) {
        events.add(LifecycleEvent(type, name, stage, instanceId, isChangingConfigurations))
        val configNote = if (isChangingConfigurations == true) " (config change)" else ""
        Log.d(TAG, "${type.name} $name#$instanceId -> $stage$configNote")
    }

}