package ai.fd.thinklet.app.lifelog.domain.di

import ai.fd.thinklet.app.lifelog.domain.MicRecordUseCase
import ai.fd.thinklet.app.lifelog.domain.SnapshotUseCase
import ai.fd.thinklet.library.lifelog.data.audioCapture.AudioCaptureRepository
import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import ai.fd.thinklet.library.lifelog.data.mic.MicRepository
import ai.fd.thinklet.library.lifelog.data.snapshot.SnapShotRepository
import ai.fd.thinklet.library.lifelog.data.timer.TimerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
object UseCaseProvider {
    @Provides
    fun provideSnapshotUseCase(
        timerRepository: TimerRepository,
        snapShotRepository: SnapShotRepository,
        fileSelectorRepository: FileSelectorRepository
    ): SnapshotUseCase {
        return SnapshotUseCase(
            timerRepository,
            snapShotRepository,
            fileSelectorRepository
        )
    }

    @Provides
    fun provideMicRecordUseCase(
        micRepository: MicRepository,
        audioCaptureRepository: AudioCaptureRepository,
        fileSelectorRepository: FileSelectorRepository
    ): MicRecordUseCase {
        return MicRecordUseCase(
            micRepository,
            audioCaptureRepository,
            fileSelectorRepository
        )
    }
}
