package dev.upyet.alarm.domain

object VolumeRamp {
    const val RAMP_DURATION_MILLIS = 30_000L
    const val START_SCALAR = 0.2f

    /** Volume scalar 0f..1f for a ring that started [elapsedMillis] ago. */
    fun scalarAt(elapsedMillis: Long, finalRing: Boolean): Float {
        if (finalRing) return 1f
        if (elapsedMillis <= 0) return START_SCALAR
        if (elapsedMillis >= RAMP_DURATION_MILLIS) return 1f
        val progress = elapsedMillis.toFloat() / RAMP_DURATION_MILLIS.toFloat()
        return START_SCALAR + (1f - START_SCALAR) * progress
    }
}
