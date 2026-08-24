package dev.myalarm.core.time

import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

interface TimeProvider {
    fun now(): Instant

    fun zone(): ZoneId
}

class SystemTimeProvider
    @Inject
    constructor() : TimeProvider {
        override fun now(): Instant = Instant.now()

        override fun zone(): ZoneId = ZoneId.systemDefault()
    }
