package io.github.darkpro1337.greenhouse

import io.github.darkpro1337.greenhouse.dto.JobsResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JobMatcherTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val jobs = json.decodeFromString<JobsResponse>(
        javaClass.getResource("/jetbrains-jobs-sample.json")!!.readText(),
    ).jobs

    @Test
    fun `empty filters match all jobs`() {
        val matched = JobMatcher.filter(jobs, WatchFilters())
        assertEquals(jobs.size, matched.size)
    }

    @Test
    fun `filters by office Armenia`() {
        val filters = WatchFilters(officeIds = setOf(4029421101L))
        val matched = JobMatcher.filter(jobs, filters)
        assertEquals(2, matched.size)
        assertTrue(matched.all { it.offices.any { office -> office.id == 4029421101L } })
    }

    @Test
    fun `filters by office and category`() {
        val filters = WatchFilters(
            officeIds = setOf(4029421101L, 4025591101L),
            metadataByField = mapOf(11295787101L to setOf("Software Development")),
        )

        val matched = JobMatcher.filter(jobs, filters)
        assertEquals(2, matched.size)
        assertEquals(
            setOf(4901288101L, 1001L),
            matched.map { it.id }.toSet(),
        )
    }

    @Test
    fun `category alone excludes sales`() {
        val filters = WatchFilters(
            metadataByField = mapOf(11295787101L to setOf("Software Development")),
        )

        val matched = JobMatcher.filter(jobs, filters)
        assertFalse(matched.any { it.title.contains("Account Manager") })
        assertEquals(2, matched.size)
    }
}

class FilterCatalogTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val jobs = json.decodeFromString<JobsResponse>(
        javaClass.getResource("/jetbrains-jobs-sample.json")!!.readText(),
    ).jobs

    @Test
    fun `builds offices and metadata from jobs`() {
        val catalog = FilterCatalog.from(offices = emptyList(), jobs = jobs)
        assertEquals(
            setOf(4029421101L, 4029424101L, 4025591101L),
            catalog.offices.map { it.id }.toSet(),
        )

        val category = catalog.metadataFields.single { it.fieldName == "Job Category" }
        assertEquals(
            listOf("IT/DevOps", "Sales", "Software Development"),
            category.values,
        )
    }
}

class GreenhouseClientNormalizeTest {
    @Test
    fun `extracts token from eu job board url`() {
        val token = GreenhouseClient.normalizeToken(
            "https://job-boards.eu.greenhouse.io/jetbrains?offices%5B%5D=4029421101",
        )
        assertEquals("jetbrains", token)
    }

    @Test
    fun `accepts plain token`() {
        assertEquals("jetbrains", GreenhouseClient.normalizeToken("JetBrains"))
    }
}
