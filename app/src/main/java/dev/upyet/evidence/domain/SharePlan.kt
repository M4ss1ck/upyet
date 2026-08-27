package dev.upyet.evidence.domain

import dev.upyet.alarm.domain.OccurrenceId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ShareableClip(val fileName: String, val outgoingName: String)

fun outgoingClipName(scheduledFor: Instant, zone: ZoneId, ordinal: Int): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.ROOT).withZone(zone)
    val base = formatter.format(scheduledFor)
    val suffix = if (ordinal <= 1) "" else "-$ordinal"
    return "upyet-$base$suffix.mp4"
}

fun shareableClips(
    chain: OccurrenceChain,
    segmentsByOccurrence: Map<OccurrenceId, List<EvidenceSegment>>,
    zone: ZoneId,
): List<ShareableClip> {
    val result = mutableListOf<ShareableClip>()
    for (link in chain.links) {
        val segments = segmentsByOccurrence[link.id] ?: emptyList()
        var ordinalInLink = 0
        for (segment in segments) {
            val fileName = segment.fileName ?: continue
            if (segment.status != EvidenceStatus.RECORDED && segment.status != EvidenceStatus.PARTIAL) continue
            ordinalInLink += 1
            val outgoing = outgoingClipName(link.scheduledFor, zone, ordinalInLink)
            result += ShareableClip(fileName = fileName, outgoingName = outgoing)
        }
    }
    return result
}
