package com.mindset.di

import com.mindset.presentation.HistoryViewModel
import com.mindset.presentation.HomeViewModel
import com.mindset.presentation.LogTabViewModel
import com.mindset.presentation.LogWorkoutViewModel
import com.mindset.presentation.OnboardingViewModel
import com.mindset.presentation.NewSessionViewModel
import com.mindset.presentation.PreferencesViewModel
import com.mindset.presentation.ProfileViewModel
import com.mindset.presentation.SessionDetailViewModel
import com.mindset.presentation.StationsViewModel
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform

/**
 * Swift-friendly entry point, callable from `iOSApp.init()`. In a clean-named file so the
 * generated Objective-C/Swift facade is the predictable `KoinIosKt`.
 */
fun doInitKoin() {
    // appObservability is a no-op unless the build opted in with `-Pmindset.profiler=true`, so
    // iOS gets the profiler on the same terms as Android with no platform-specific gate.
    initKoin(observability = appObservability)
}

fun homeViewModel(): HomeViewModel = KoinPlatform.getKoin().get()
fun logTabViewModel(): LogTabViewModel = KoinPlatform.getKoin().get()
fun stationsViewModel(): StationsViewModel = KoinPlatform.getKoin().get()
fun profileViewModel(): ProfileViewModel = KoinPlatform.getKoin().get()

/**
 * First-run setup. [OnboardingViewModel.onComplete] is the one call that makes both iOS tabs useful:
 * it saves the athlete profile, creates the upcoming RaceGoal (Home's countdown widget) and writes
 * the division/gender/race-mode preferences (without which StationsViewModel returns an empty board).
 */
fun onboardingViewModel(): OnboardingViewModel = KoinPlatform.getKoin().get()
fun historyViewModel(): HistoryViewModel = KoinPlatform.getKoin().get()

/**
 * Resolve the shared [HistoryViewModel] for Swift. The ViewModel graph lives in `commonMain`; this
 * is the one Swift-callable seam that hands an instance across (Swift holds it in an ObservableObject).
 */

fun preferencesViewModel(): PreferencesViewModel = KoinPlatform.getKoin().get()

/** Parameterized: the [SessionDetailViewModel] factory takes the sessionId via Koin `parametersOf`. */
fun sessionDetailViewModel(sessionId: String): SessionDetailViewModel = KoinPlatform.getKoin().get { parametersOf(sessionId) }

// `do` prefix (as with [doInitKoin]): Kotlin/Native mangles Obj-C selectors starting with `new`
// (the ARC "new" method family), so a bare `newSessionViewModel()` would surface to Swift under a
// surprising auto-generated name. Naming it explicitly keeps Kotlin and Swift in sync.
fun doNewSessionViewModel(): NewSessionViewModel = KoinPlatform.getKoin().get()

fun logWorkoutViewModel(sessionId: String): LogWorkoutViewModel = KoinPlatform.getKoin().get { parametersOf(sessionId) }
