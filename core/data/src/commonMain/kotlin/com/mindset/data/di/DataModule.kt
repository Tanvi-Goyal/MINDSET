package com.mindset.data.di

import com.mindset.data.ActiveWorkoutControllerImpl
import com.mindset.data.AthleteProfileRepositoryImpl
import com.mindset.data.EntitlementRepositoryImpl
import com.mindset.data.MuscleImageProviderImpl
import com.mindset.data.PersonalRecordRepositoryImpl
import com.mindset.data.PreferencesRepositoryImpl
import com.mindset.data.RaceCalendarRepositoryImpl
import com.mindset.data.RaceGoalRepositoryImpl
import com.mindset.data.SessionRepositoryImpl
import com.mindset.domain.ActiveWorkoutController
import com.mindset.domain.MuscleImageProvider
import com.mindset.domain.repository.AthleteProfileRepository
import com.mindset.domain.repository.EntitlementRepository
import com.mindset.domain.repository.PersonalRecordRepository
import com.mindset.data.ActiveRaceStoreImpl
import com.mindset.domain.repository.ActiveRaceStore
import com.mindset.domain.repository.PreferencesRepository
import com.mindset.domain.repository.RaceCalendarRepository
import com.mindset.domain.repository.RaceGoalRepository
import com.mindset.domain.repository.SessionRepository
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Data graph: repository impls bound to their [com.mindset.domain] interfaces (so consumers depend
 * on the interface, never the impl), plus the wger muscle-diagram provider. Plain constructor
 * wiring — no class annotations.
 */
val dataModule =
    module {
        single { SessionRepositoryImpl(get(), get(), get(), get()) } bind SessionRepository::class
        // App-scoped live-workout timer: a single instance shared by every surface (owns its own scope).
        single { ActiveWorkoutControllerImpl(get(), get(), get(), get()) } bind ActiveWorkoutController::class
        single { PreferencesRepositoryImpl(get()) } bind PreferencesRepository::class
        single { ActiveRaceStoreImpl(get()) } bind ActiveRaceStore::class
        single { AthleteProfileRepositoryImpl(get()) } bind AthleteProfileRepository::class
        single { RaceGoalRepositoryImpl(get(), get(), get()) } bind RaceGoalRepository::class
        single { RaceCalendarRepositoryImpl(get(), get(), get()) } bind RaceCalendarRepository::class
        single { EntitlementRepositoryImpl(get()) } bind EntitlementRepository::class
        single { PersonalRecordRepositoryImpl(get()) } bind PersonalRecordRepository::class
        single { MuscleImageProviderImpl(get()) } bind MuscleImageProvider::class
    }
