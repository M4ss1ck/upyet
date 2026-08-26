package dev.upyet.evidence.domain

import dev.upyet.alarm.domain.OccurrenceId

/** One wake-up: the alarm that rang plus every snooze that followed it. */
data class OccurrenceChain(val links: List<AlarmOccurrence>) {
    init {
        require(links.isNotEmpty()) { "OccurrenceChain requires at least one link" }
    }

    val root: AlarmOccurrence = links.first()
    val last: AlarmOccurrence = links.last()
    val snoozeCount: Int = links.count { it.outcome == OccurrenceOutcome.SNOOZED }
    val containsSnooze: Boolean = snoozeCount > 0
    val finalOutcome: OccurrenceOutcome = last.outcome

    /** Wall-clock from the first ring to however the chain ended. */
    val elapsedMillis: Long = run {
        val start = root.scheduledFor.toEpochMilli()
        val endInstant = last.dismissedAt ?: last.triggeredAt ?: last.scheduledFor
        val end = endInstant.toEpochMilli()
        maxOf(0L, end - start)
    }
}

/** Groups occurrences into chains. Newest chain (by root.scheduledFor) first. */
fun buildOccurrenceChains(occurrences: List<AlarmOccurrence>): List<OccurrenceChain> {
    if (occurrences.isEmpty()) return emptyList()
    val byId = occurrences.associateBy { it.id }
    val childrenByParent = occurrences
        .mapNotNull { occurrence -> occurrence.parentOccurrenceId?.takeIf(byId::containsKey)?.let { it to occurrence } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, children) -> children.sortedWith(compareBy({ it.scheduledFor }, { it.id.value })) }

    val claimed = mutableSetOf<OccurrenceId>()
    val chains = mutableListOf<OccurrenceChain>()

    // `claimed` doubles as the cycle guard: an occurrence already in a chain is never walked again.
    fun walkFrom(start: AlarmOccurrence) {
        val links = mutableListOf<AlarmOccurrence>()
        var current: AlarmOccurrence? = start
        while (current != null && claimed.add(current.id)) {
            links += current
            current = childrenByParent[current.id]?.firstOrNull { it.id !in claimed }
        }
        if (links.isNotEmpty()) chains += OccurrenceChain(links.sortedWith(compareBy({ it.scheduledFor }, { it.id.value })))
    }

    // A root has no parent, or has one that is not in the list: retention may have deleted it, or the
    // snooze happened before first unlock and never got an id. Either way it starts its own wake-up
    // rather than disappearing from history.
    occurrences.filter { it.parentOccurrenceId?.let(byId::containsKey) != true }.forEach(::walkFrom)
    // Whatever is left is reachable only through a parent cycle, which nothing should write. Walking it
    // here keeps a corrupt row visible instead of silently dropping it.
    occurrences.filterNot { it.id in claimed }.forEach(::walkFrom)

    return chains.sortedByDescending { it.root.scheduledFor }
}

/** The clip a rolled-up row shows: the final link's first clip, falling back through earlier links. */
fun chainThumbnailSourceFileName(chain: OccurrenceChain, segmentsByOccurrence: Map<OccurrenceId, List<EvidenceSegment>>): String? {
    for (link in chain.links.asReversed()) {
        val segments = segmentsByOccurrence[link.id] ?: emptyList()
        val fileName = thumbnailSourceFileName(segments)
        if (fileName != null) return fileName
    }
    return null
}
