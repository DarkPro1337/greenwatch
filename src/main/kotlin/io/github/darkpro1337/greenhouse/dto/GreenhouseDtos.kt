package io.github.darkpro1337.greenhouse.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class JobsResponse(
    val jobs: List<GreenhouseJob> = emptyList(),
    val meta: JobsMeta? = null,
)

@Serializable
data class JobsMeta(
    val total: Int? = null,
)

@Serializable
data class GreenhouseJob(
    val id: Long,
    val title: String,
    @SerialName("absolute_url") val absoluteUrl: String,
    val location: JobLocation? = null,
    val offices: List<GreenhouseOffice> = emptyList(),
    val departments: List<GreenhouseDepartment> = emptyList(),
    val metadata: List<JobMetadata>? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("company_name") val companyName: String? = null,
)

@Serializable
data class JobLocation(
    val name: String? = null,
)

@Serializable
data class GreenhouseOffice(
    val id: Long,
    val name: String,
    val location: String? = null,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("child_ids") val childIds: List<Long> = emptyList(),
)

@Serializable
data class GreenhouseDepartment(
    val id: Long,
    val name: String,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("child_ids") val childIds: List<Long> = emptyList(),
)

@Serializable
data class JobMetadata(
    val id: Long,
    val name: String,
    val value: JsonElement? = null,
    @SerialName("value_type") val valueType: String? = null,
) {
    fun valueAsStrings(): List<String> {
        val element = value ?: return emptyList()
        return when {
            element is JsonPrimitive -> listOfNotNull(element.contentOrNull?.takeIf { it.isNotBlank() })
            else -> runCatching {
                element.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { v -> v.isNotBlank() } }
            }.getOrElse {
                listOf(element.toString().trim('"')).filter { it.isNotBlank() }
            }
        }
    }
}
