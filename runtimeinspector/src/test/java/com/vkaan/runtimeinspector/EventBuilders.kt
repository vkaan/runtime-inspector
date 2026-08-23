package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.cardservice.CardServiceState
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

fun cardServiceEvent(
    seq: Long,
    from: CardServiceState,
    to: CardServiceState,
    apis: List<CardServiceApi> = emptyList(),
    line: String = "test line",
    nanos: Long = seq,
) = RuntimeEvent.CardService(
    seq = seq,
    timestampMillis = seq,
    elapsedRealtimeNanos = nanos,
    from = from,
    to = to,
    apis = apis,
    line = line,
)

fun cardServiceCall(
    seq: Long,
    apis: List<CardServiceApi>,
    state: CardServiceState = CardServiceState.IDLE,
    to: CardServiceState = state,
    line: String = "test line",
    nanos: Long = seq,
) = RuntimeEvent.CardService(
    seq = seq,
    timestampMillis = seq,
    elapsedRealtimeNanos = nanos,
    from = state,
    to = to,
    apis = apis,
    line = line,
)

fun mb(n: Int): Long = n * 1024L * 1024L
