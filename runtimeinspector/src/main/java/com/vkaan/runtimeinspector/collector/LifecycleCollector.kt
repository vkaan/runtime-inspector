package com.vkaan.runtimeinspector.collector

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.SourceType
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage
import com.vkaan.runtimeinspector.timeline.Timeline

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
        recordActivity(activity, Stage.CREATED)
        if (activity is FragmentActivity) {
            // Both listeners are created per Activity so they can capture which Activity they
            // belong to; a single shared instance could not tell the callbacks apart.
            val hostId = System.identityHashCode(activity)
            val hostName = activity::class.java.simpleName
            val fm = activity.supportFragmentManager
            fm.registerFragmentLifecycleCallbacks(fragmentCallbacks(hostId, fm), true)
            fm.addOnBackStackChangedListener(backStackListener(hostId, hostName))
        }
        HeapSampler.sample(timeline, RuntimeEvent.MemoryUsage.Trigger.ACTIVITY_CREATED)
    }



    override fun onActivityStarted(activity: Activity) = recordActivity(activity, Stage.STARTED)
    override fun onActivityResumed(activity: Activity) = recordActivity(activity, Stage.RESUMED)
    override fun onActivityPaused(activity: Activity) = recordActivity(activity, Stage.PAUSED)
    override fun onActivityStopped(activity: Activity) = recordActivity(activity, Stage.STOPPED)
    override fun onActivityDestroyed(activity: Activity) {
        recordActivity(activity, Stage.DESTROYED)
        HeapSampler.sample(timeline, RuntimeEvent.MemoryUsage.Trigger.ACTIVITY_DESTROYED)
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    // The callbacks' own `fm` argument is ignored throughout: with recursive registration it is
    // the CHILD FragmentManager for nested fragments, whose back stack is not the host's.
    // `hostFm` is captured once per Activity and is the one we want to measure.
    private fun fragmentCallbacks(hostActivityId: Int, hostFm: FragmentManager) =
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentAttached(fm: FragmentManager, f: Fragment, ctx: Context) =
                recordFragment(f, hostActivityId, hostFm, Stage.ATTACHED)
            override fun onFragmentCreated(fm: FragmentManager, f: Fragment, s: Bundle?) =
                recordFragment(f, hostActivityId, hostFm, Stage.CREATED)
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, s: Bundle?) =
                recordFragment(f, hostActivityId, hostFm, Stage.VIEW_CREATED)
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) =
                recordFragment(f, hostActivityId, hostFm, Stage.STARTED)
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) =
                recordFragment(f, hostActivityId, hostFm, Stage.RESUMED)
            override fun onFragmentPaused(fm: FragmentManager, f: Fragment) =
                recordFragment(f, hostActivityId, hostFm, Stage.PAUSED)
            override fun onFragmentStopped(fm: FragmentManager, f: Fragment) =
                recordFragment(f, hostActivityId, hostFm, Stage.STOPPED)
            override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) =
                recordFragment(f, hostActivityId, hostFm, Stage.VIEW_DESTROYED)
            override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) =
                recordFragment(f, hostActivityId, hostFm, Stage.DESTROYED)
            override fun onFragmentDetached(fm: FragmentManager, f: Fragment) =
                recordFragment(f, hostActivityId, hostFm, Stage.DETACHED)
        }

    private fun backStackListener(
        hostActivityId: Int,
        hostActivityName: String,
    ) = object : FragmentManager.OnBackStackChangedListener {

        // Fragment 1.4.0+ fires onBackStackChangeCommitted once PER FRAGMENT, so a replace()
        // onto an occupied container reports twice for one navigation. All committed callbacks
        // and then onBackStackChanged() run in the same main-thread execution, so buffer here
        // and flush one event per transaction when onBackStackChanged() arrives.
        private val pendingFragments = mutableListOf<String>()
        private var pendingPop = false

        override fun onBackStackChangeCommitted(fragment: Fragment, pop: Boolean) {
            pendingFragments += fragment::class.java.simpleName
            pendingPop = pop
        }

        override fun onBackStackChanged() {
            if (pendingFragments.isEmpty()) return
            val fragmentNames = pendingFragments.toList()
            val popped = pendingPop
            pendingFragments.clear()
            timeline.record { seq ->
                RuntimeEvent.BackStack(
                    seq = seq,
                    timestampMillis = System.currentTimeMillis(),
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                    hostActivityId = hostActivityId,
                    hostActivityName = hostActivityName,
                    fragmentNames = fragmentNames,
                    popped = popped,
                )
            }
        }
    }

    // Recording

    private fun recordActivity(activity: Activity, stage: Stage) =
        record(
            sourceType = SourceType.ACTIVITY,
            name = activity::class.java.simpleName,
            stage = stage,
            instanceId = System.identityHashCode(activity),
            isChangingConfigurations =
                if (stage == Stage.STOPPED || stage == Stage.DESTROYED) activity.isChangingConfigurations
                else null,
            backStackEntryCount = (activity as? FragmentActivity)?.supportFragmentManager?.backStackEntryCount,
        )

    private fun recordFragment(
        fragment: Fragment,
        hostActivityId: Int,
        hostFm: FragmentManager,
        stage: Stage,
    ) = record(
        sourceType = SourceType.FRAGMENT,
        name = fragment::class.java.simpleName,
        stage = stage,
        instanceId = System.identityHashCode(fragment),
        hostActivityId = hostActivityId,
        // Fragment lifecycle callbacks run after the transaction is applied, so this is settled.
        backStackEntryCount = hostFm.backStackEntryCount,
    )

    private fun record(
        sourceType: SourceType,
        name: String,
        stage: Stage,
        instanceId: Int,
        isChangingConfigurations: Boolean? = null,
        hostActivityId: Int? = null,
        backStackEntryCount: Int? = null,
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
                hostActivityId = hostActivityId,
                backStackEntryCount = backStackEntryCount,
            )
        }
    }
}
