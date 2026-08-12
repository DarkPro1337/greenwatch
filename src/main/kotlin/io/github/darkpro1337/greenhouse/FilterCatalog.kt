package io.github.darkpro1337.greenhouse

import io.github.darkpro1337.greenhouse.dto.GreenhouseJob
import io.github.darkpro1337.greenhouse.dto.GreenhouseOffice

data class MetadataFieldOption(
    val fieldId: Long,
    val fieldName: String,
    val values: List<String>,
)

data class FilterCatalog(
    val offices: List<GreenhouseOffice>,
    val metadataFields: List<MetadataFieldOption>,
) {
    companion object {
        fun from(offices: List<GreenhouseOffice>, jobs: List<GreenhouseJob>): FilterCatalog {
            val officeMap = linkedMapOf<Long, GreenhouseOffice>()
            offices.forEach { officeMap[it.id] = it }
            jobs.forEach { job ->
                job.offices.forEach { office -> officeMap.putIfAbsent(office.id, office) }
            }

            val valuesByField = linkedMapOf<Long, Pair<String, LinkedHashSet<String>>>()
            jobs.forEach { job ->
                job.metadata.orEmpty().forEach { meta ->
                    val values = meta.valueAsStrings()
                    if (values.isEmpty()) return@forEach
                    val bucket = valuesByField.getOrPut(meta.id) {
                        meta.name to linkedSetOf()
                    }
                    bucket.second.addAll(values)
                }
            }

            val metadataFields = valuesByField.map { (fieldId, pair) ->
                MetadataFieldOption(
                    fieldId = fieldId,
                    fieldName = pair.first,
                    values = pair.second.sorted(),
                )
            }.sortedBy { it.fieldName }

            return FilterCatalog(
                offices = officeMap.values.sortedBy { it.name.lowercase() },
                metadataFields = metadataFields,
            )
        }
    }
}
