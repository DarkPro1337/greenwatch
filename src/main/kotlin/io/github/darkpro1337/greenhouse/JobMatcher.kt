package io.github.darkpro1337.greenhouse

import io.github.darkpro1337.greenhouse.dto.GreenhouseJob

data class WatchFilters(
    val officeIds: Set<Long> = emptySet(),
    val metadataByField: Map<Long, Set<String>> = emptyMap(),
)

object JobMatcher {
    fun matches(job: GreenhouseJob, filters: WatchFilters): Boolean {
        if (filters.officeIds.isNotEmpty()) {
            val jobOfficeIds = job.offices.map { it.id }.toSet()
            if (jobOfficeIds.intersect(filters.officeIds).isEmpty())
                return false
        }

        filters.metadataByField.forEach { (fieldId, allowedValues) ->
            if (allowedValues.isEmpty()) return@forEach
            val jobValues = job.metadata
                .orEmpty()
                .filter { it.id == fieldId }
                .flatMap { it.valueAsStrings() }
                .toSet()

            if (jobValues.intersect(allowedValues).isEmpty())
                return false
        }

        return true
    }

    fun filter(jobs: List<GreenhouseJob>, filters: WatchFilters): List<GreenhouseJob> {
        return jobs.filter { matches(it, filters) }
    }
}
