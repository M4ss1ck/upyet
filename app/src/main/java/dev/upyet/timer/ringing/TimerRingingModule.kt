package dev.upyet.timer.ringing

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.upyet.evidence.camera.EvidenceCoordinator
import dev.upyet.evidence.camera.EvidenceRecorder

/** Direct-Boot safe: neither binding builds Room or DataStore (AGENTS.md §11, §14). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimerRingingModule {
    @Binds
    abstract fun bindTimerRingingCommands(commands: ServiceTimerRingingCommands): TimerRingingCommands

    @Binds
    abstract fun bindEvidenceRecorder(coordinator: EvidenceCoordinator): EvidenceRecorder
}
