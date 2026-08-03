package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.timeline.RuntimeEvent
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.SourceType
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage

fun activityEvent(
    seq: Long,
    id: Int,
    stage: Stage,
    name: String = "TestActivity",
    configChange: Boolean? = null,
    backStackCount: Int? = null,
    nanos: Long = seq,
) = RuntimeEvent.Lifecycle(
    seq = seq,
    timestampMillis = seq,
    elapsedRealtimeNanos = nanos,
    sourceType = SourceType.ACTIVITY,
    name = name,
    stage = stage,
    instanceId = id,
    isChangingConfigurations = configChange,
    backStackEntryCount = backStackCount,
)

fun fragmentEvent(
    seq: Long,
    id: Int,
    stage: Stage,
    hostId: Int,
    name: String = "TestFragment",
    backStackCount: Int? = null,
    nanos: Long = seq,
) = RuntimeEvent.Lifecycle(
    seq = seq,
    timestampMillis = seq,
    elapsedRealtimeNanos = nanos,
    sourceType = SourceType.FRAGMENT,
    name = name,
    stage = stage,
    instanceId = id,
    hostActivityId = hostId,
    backStackEntryCount = backStackCount,
)

fun processEvent(
    seq: Long,
    state: RuntimeEvent.Process.State,
    nanos: Long = seq,
) = RuntimeEvent.Process(
    seq = seq,
    timestampMillis = seq,
    elapsedRealtimeNanos = nanos,
    state = state,
)

fun memoryEvent(
    seq: Long,
    level: Int,
    nanos: Long = seq,
) = RuntimeEvent.Memory(
    seq = seq,
    timestampMillis = seq,
    elapsedRealtimeNanos = nanos,
    level = level,
)

fun heapEvent(
    seq: Long,
    usedBytes: Long,
    maxBytes: Long,
    trigger: RuntimeEvent.MemoryUsage.Trigger,
    nanos: Long = seq,
) = RuntimeEvent.MemoryUsage(
    seq = seq,
    timestampMillis = seq,
    elapsedRealtimeNanos = nanos,
    usedBytes = usedBytes,
    maxBytes = maxBytes,
    trigger = trigger,
)

fun networkEvent(
    seq: Long,
    state: RuntimeEvent.Network.State,
    transports: List<String> = emptyList(),
    nanos: Long = seq,
) = RuntimeEvent.Network(
    seq = seq,
    timestampMillis = seq,
    elapsedRealtimeNanos = nanos,
    state = state,
    transports = transports,
)

fun crashEvent(
    seq: Long,
    exceptionClass: String = "java.lang.RuntimeException",
    nanos: Long = seq,
) = RuntimeEvent.Crash(
    seq = seq,
    timestampMillis = seq,
    elapsedRealtimeNanos = nanos,
    threadName = "main",
    exceptionClass = exceptionClass,
    exceptionMessage = "test crash",
    topFrame = null,
)

fun systemEvent(
    seq: Long,
    signal: RuntimeEvent.SystemSignal.Signal,
    nanos: Long = seq,
) = RuntimeEvent.SystemSignal(
    seq = seq,
    timestampMillis = seq,
    elapsedRealtimeNanos = nanos,
    signal = signal,
)

fun mb(n: Int): Long = n * 1024L * 1024L
