package com.mindset.di

import com.mindset.FlowSubscription
import com.mindset.domain.MuscleImageProvider
import com.mindset.domain.repository.SessionRepository
import com.mindset.model.Exercise
import com.mindset.model.MuscleDiagram
import com.mindset.presentation.HistoryViewModel
import com.mindset.presentation.HomeViewModel
import com.mindset.presentation.LogWorkoutViewModel
import com.mindset.presentation.NewSessionViewModel
import com.mindset.presentation.PreferencesViewModel
import com.mindset.presentation.SessionDetailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

/**
 * Resolve the shared [HistoryViewModel] for Swift. The ViewModel graph lives in `commonMain`; this
 * is the one Swift-callable seam that hands an instance across (Swift holds it in an ObservableObject).
 */
fun historyViewModel(): HistoryViewModel = KoinPlatform.getKoin().get()

fun homeViewModel(): HomeViewModel = KoinPlatform.getKoin().get()

fun preferencesViewModel(): PreferencesViewModel = KoinPlatform.getKoin().get()

/** Parameterized: the [SessionDetailViewModel] factory takes the sessionId via Koin `parametersOf`. */
fun sessionDetailViewModel(sessionId: String): SessionDetailViewModel = KoinPlatform.getKoin().get { parametersOf(sessionId) }

// `do` prefix (as with [doInitKoin]): Kotlin/Native mangles Obj-C selectors starting with `new`
// (the ARC "new" method family), so a bare `newSessionViewModel()` would surface to Swift under a
// surprising auto-generated name. Naming it explicitly keeps Kotlin and Swift in sync.
fun doNewSessionViewModel(): NewSessionViewModel = KoinPlatform.getKoin().get()

fun logWorkoutViewModel(sessionId: String): LogWorkoutViewModel = KoinPlatform.getKoin().get { parametersOf(sessionId) }

/**
 * Load the full exercise catalog for the iOS picker. The shared [ExerciseLibraryViewModel] exposes
 * `Flow<PagingData<Exercise>>` (Paging 3), which has no Swift consumer — so on iOS we read the
 * bounded catalog directly and filter client-side. Returns a [FlowSubscription] for cancellation.
 */
fun loadExercises(onResult: (List<Exercise>) -> Unit): FlowSubscription {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    scope.launch {
        val catalog = KoinPlatform.getKoin().get<SessionRepository>().exercisesById()
        onResult(catalog.values.toList())
    }
    return FlowSubscription(scope)
}

/** wger muscle diagram (base body + overlay URLs) for a muscle name; suspend → Swift async. Null if unmapped/offline. */
suspend fun muscleDiagram(muscleName: String): MuscleDiagram? = KoinPlatform.getKoin().get<MuscleImageProvider>().diagram(muscleName)
