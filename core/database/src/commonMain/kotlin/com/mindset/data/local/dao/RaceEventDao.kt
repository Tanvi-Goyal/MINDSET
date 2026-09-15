package com.mindset.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.mindset.data.local.RaceEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RaceEventDao {
    /**
     * Cities with at least one race that hasn't finished yet, nearest race first. A city hosting
     * several races appears once — the picker selects a city, then a date within it.
     */
    @Query(
        "SELECT city, country FROM race_event WHERE endDate >= :now AND formatKey = :formatKey " +
            "GROUP BY city, country ORDER BY MIN(startDate)",
    )
    fun observeCities(now: Long, formatKey: String): Flow<List<RaceCityRow>>

    /** Every upcoming race in [city], soonest first. */
    @Query(
        "SELECT * FROM race_event WHERE city = :city AND country = :country AND endDate >= :now " +
            "AND formatKey = :formatKey ORDER BY startDate",
    )
    fun observeByCity(city: String, country: String, now: Long, formatKey: String): Flow<List<RaceEventEntity>>

    @Upsert
    suspend fun upsertAll(events: List<RaceEventEntity>)

    @Query("SELECT COUNT(*) FROM race_event")
    suspend fun count(): Int
}

/** Projection for [RaceEventDao.observeCities] — one row per distinct place, not per race. */
data class RaceCityRow(val city: String, val country: String)
