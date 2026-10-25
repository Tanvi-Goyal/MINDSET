package com.mindset.data.local

import com.mindset.model.HyroxStation
import com.mindset.model.MetricType
import com.mindset.model.Modality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parsing + modality/metric classification of the free-exercise-db shape, plus the curated seed. */
class ExerciseImporterTest {

    private val sample = """
    [
      { "id": "Barbell_Bench_Press", "name": "Barbell Bench Press", "equipment": "barbell",
        "primaryMuscles": ["chest"], "secondaryMuscles": ["shoulders", "triceps"],
        "instructions": ["Lie on the bench.", "Press up."], "category": "strength",
        "images": ["Barbell_Bench_Press/0.jpg"] },
      { "id": "Running", "name": "Running", "equipment": null, "primaryMuscles": ["quadriceps"],
        "secondaryMuscles": [], "instructions": ["Run."], "category": "cardio", "images": [] },
      { "id": "Pushups", "name": "Pushups", "equipment": "body only", "primaryMuscles": ["chest"],
        "secondaryMuscles": [], "instructions": ["Push."], "category": "strength", "images": [] },
      { "id": "Hamstring_Stretch", "name": "Hamstring Stretch", "equipment": null,
        "primaryMuscles": ["hamstrings"], "secondaryMuscles": [], "instructions": ["Hold."],
        "category": "stretching", "images": [] },
      { "id": "Box_Jump", "name": "Box Jump", "equipment": "other", "primaryMuscles": ["quadriceps"],
        "secondaryMuscles": [], "instructions": ["Jump."], "category": "plyometrics", "images": [] }
    ]
    """.trimIndent()

    @Test
    fun parse_mapsFieldsAndDerivesMetric() {
        val result = ExerciseImporter.parse(sample)
        assertEquals(5, result.size)

        val bench = result.first { it.id == "Barbell_Bench_Press" }
        assertEquals(ExerciseMetric.WEIGHT_REPS, bench.metric, "legacy metric preserved")
        assertEquals("barbell", bench.equipment)
        assertEquals(listOf("chest"), bench.primaryMusclesList)
        assertEquals(2, bench.instructionsList.size)
        assertTrue(bench.imageUrlsList.first().startsWith("https://raw.githubusercontent.com/"))
        assertTrue("chest" in bench.keywords && "barbell" in bench.keywords)
    }

    @Test
    fun classifies_modality_and_default_metric_by_category() {
        val byId = ExerciseImporter.parse(sample).associateBy { it.id }

        fun assertClass(id: String, modality: Modality, metric: MetricType) {
            assertEquals(modality.name, byId.getValue(id).modality, "$id modality")
            assertEquals(metric.name, byId.getValue(id).defaultMetric, "$id metric")
        }

        assertClass("Barbell_Bench_Press", Modality.STRENGTH, MetricType.WEIGHT_REPS)
        assertClass("Running", Modality.RUN, MetricType.DISTANCE_TIME) // name split out of conditioning
        assertClass("Pushups", Modality.STRENGTH, MetricType.REPS_ONLY) // bodyweight → reps only
        assertClass("Hamstring_Stretch", Modality.MOBILITY, MetricType.DURATION)
        assertClass("Box_Jump", Modality.CONDITIONING, MetricType.REPS_ONLY)
        // Regular library rows are never tagged as Hyrox stations.
        assertTrue(byId.values.all { it.hyroxStation == null })
    }

    @Test
    fun supplemental_seed_has_all_eight_hyrox_stations_once() {
        val seed = ExerciseImporter.supplementalSeed()
        val stations = seed.mapNotNull { it.hyroxStation }
        assertEquals(HyroxStation.entries.map { it.name }.toSet(), stations.toSet(), "all 8 stations")
        assertEquals(HyroxStation.entries.size, stations.size, "each station seeded exactly once")
        assertEquals("REPS_TIME", seed.first { it.hyroxStation == HyroxStation.WALL_BALLS.name }.defaultMetric)
    }

    @Test
    fun every_metric_type_is_reachable_across_the_seeded_catalog() {
        val metrics = (ExerciseImporter.parse(sample) + ExerciseImporter.supplementalSeed())
            .mapNotNull { it.defaultMetric }
            .toSet()
        // v1 seeds Hyrox only; CALORIES returns with the conditioning machines (see the phase-2
        // TODO in SessionRepositoryImpl.ensureSeeded).
        val expected = (MetricType.entries - MetricType.CALORIES).map { it.name }.toSet()
        assertEquals(expected, metrics, "all v1 capture metrics present")
    }
}
