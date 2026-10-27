package dev.upyet.testing

import dev.upyet.core.directboot.PendingOccurrenceCodec
import dev.upyet.core.directboot.PendingOccurrences
import dev.upyet.core.directboot.PendingRecord

/** In-memory [PendingOccurrences] with the real store's upsert-by-identity semantics. */
class FakePendingOccurrences(vararg initial: PendingRecord) : PendingOccurrences {
    private val records = initial.toMutableList()

    var cleared = 0
        private set

    override fun append(record: PendingRecord) {
        val identity = PendingOccurrenceCodec.identity(record)
        records.removeAll { PendingOccurrenceCodec.identity(it) == identity }
        records += record
    }

    override fun replaceIfPending(record: PendingRecord): Boolean {
        val identity = PendingOccurrenceCodec.identity(record)
        if (records.none { PendingOccurrenceCodec.identity(it) == identity }) return false
        append(record)
        return true
    }

    override fun all(): List<PendingRecord> = records.toList()

    override fun clear() {
        cleared++
        records.clear()
    }
}
