package com.mindset.race

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mindset.domain.ActiveWorkoutController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Keeps a live HYROX race running while the app is backgrounded, and surfaces it as an ongoing
 * notification the athlete can drive without unlocking.
 *
 * Holds **no state of its own** — it observes the app-scoped [ActiveWorkoutController] and forwards
 * notification taps back to it, so the notification, the timer sheet, the minimized pill and the Home
 * card are all views of one source and can never disagree.
 */
class RaceTimerService :
    Service(),
    KoinComponent {

    private val controller: ActiveWorkoutController by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RaceNotifications.ensureChannel(this)
        observeRace()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ALWAYS first, before reading any state: missing startForeground within ~5s of
        // startForegroundService is an ANR-class crash, so a null race must still post a placeholder
        // rather than skip the call.
        promoteToForeground()

        when (intent?.action) {
            RaceNotifications.ACTION_TOGGLE_PAUSE ->
                if (controller.state.value?.paused == true) controller.resume() else controller.pause()

            RaceNotifications.ACTION_NEXT -> controller.next()
        }

        return START_NOT_STICKY
    }

    /**
     * Repost only when something the notification actually draws changes.
     *
     * [ActiveWorkoutController.state] re-emits every ~200ms, so mapping to [RaceNotice] — which
     * excludes elapsed time from its equality — and de-duplicating collapses ~4,200 rebuilds per race
     * down to roughly 20. The clock itself is ticked by SystemUI via the notification's chronometer.
     */
    private fun observeRace() {
        scope.launch {
            controller.state
                .map { race ->
                    race?.let {
                        RaceNotice(
                            stepTitle = it.current?.title.orEmpty(),
                            stepIndex = it.currentIndex,
                            totalSteps = it.totalSteps,
                            paused = it.paused,
                            finished = it.finished,
                        )
                    }
                }
                .distinctUntilChanged()
                .collect { notice ->
                    if (notice == null || notice.finished) {
                        ServiceCompat.stopForeground(this@RaceTimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collect
                    }

                    // Android 13+ needs POST_NOTIFICATIONS to update the notification; the timer
                    // itself keeps running without it.
                    val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(this@RaceTimerService, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    if (!canNotify) return@collect

                    NotificationManagerCompat.from(this@RaceTimerService)
                        .notify(
                            RaceNotifications.NOTIFICATION_ID,
                            RaceNotifications.build(
                                context = this@RaceTimerService,
                                race = notice,
                                totalElapsedMs = controller.state.value?.totalElapsedMs ?: 0L,
                            ),
                        )
                }
        }
    }

    private fun promoteToForeground() {
        val race = controller.state.value
        val notice = RaceNotice(
            stepTitle = race?.current?.title.orEmpty(),
            stepIndex = race?.currentIndex ?: 0,
            totalSteps = race?.totalSteps ?: 0,
            paused = race?.paused ?: false,
            finished = race?.finished ?: false,
        )

        ServiceCompat.startForeground(
            this,
            RaceNotifications.NOTIFICATION_ID,
            RaceNotifications.build(this, notice, race?.totalElapsedMs ?: 0L),
            // ServiceCompat picks the no-type overload below API 29, so no version branch is needed.
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, RaceTimerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RaceTimerService::class.java))
        }
    }
}
