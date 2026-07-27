package com.vkaan.runtimeinspector

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

internal class LifecycleCollector(
    private val timeline: Timeline,
) : Collector, Application.ActivityLifecycleCallbacks {

    private var application: Application? = null

    override fun start(application: Application) {
        this.application = application
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun stop() {
        application?.unregisterActivityLifecycleCallbacks(this)
        application = null
        // Per-Activity FragmentLifecycleCallbacks are owned by each FragmentManager and
        // released when the host Activity is destroyed — nothing to unregister here.
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
            sourceType = RuntimeEvent.Lifecycle.SourceType.ACTIVITY,
            name = activity::class.java.simpleName,
            stage = stage,
            instanceId = System.identityHashCode(activity),
            isChangingConfigurations =
                if (stage == "STOPPED" || stage == "DESTROYED") activity.isChangingConfigurations
                else null,
        )

    private fun recordFragment(fragment: Fragment, stage: String) =
        record(
            sourceType = RuntimeEvent.Lifecycle.SourceType.FRAGMENT,
            name = fragment::class.java.simpleName,
            stage = stage,
            instanceId = System.identityHashCode(fragment),
        )

    private fun record(
        sourceType: RuntimeEvent.Lifecycle.SourceType,
        name: String,
        stage: String,
        instanceId: Int,
        isChangingConfigurations: Boolean? = null,
    ) {
        timeline.record { seq ->
            RuntimeEvent.Lifecycle(
                seq = seq,
                timestampMillis = System.currentTimeMillis(),
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                sourceType = sourceType,
                name = name,
                stage = stage,
                instanceId = instanceId,
                isChangingConfigurations = isChangingConfigurations,
            )
        }
    }
}