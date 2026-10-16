package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MatchRequest(
  val pncNumber: String? = null,
  val firstName: String? = null,
  val surname: String,
  val dateOfBirth: String? = null,
)

data class MatchResponse(
  val matches: List<Match>? = null,
  val matchedBy: OffenderSearchMatchType? = null,
) {
  @get:JsonIgnore
  val matchCount: Int get() = matches?.size ?: 0

  @get:JsonIgnore
  val isExactOffenderMatch: Boolean get() = matchCount == 1 && matchedBy == OffenderSearchMatchType.ALL_SUPPLIED
}

enum class OffenderSearchMatchType {
  ALL_SUPPLIED,
  ALL_SUPPLIED_ALIAS,
  HMPPS_KEY,
  EXTERNAL_KEY,
  NAME,
  PARTIAL_NAME,
  PARTIAL_NAME_DOB_LENIENT,
  NOTHING,
}

data class Match(val offender: OSOffender? = null)

data class OSOffender(
  val firstName: String? = null,
  val surname: String? = null,
  val dateOfBirth: String? = null,
  val otherIds: OtherIds? = null,
  val probationStatus: ProbationStatusDetail? = null,
  val offenderAliases: List<OffenderAlias>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OtherIds(
  val crn: String? = null,
  val croNumber: String? = null,
  val pncNumber: String? = null,
)

data class ProbationStatusDetail(
  val status: String? = null,
  val previouslyKnownTerminationDate: LocalDate? = null,
  val inBreach: Boolean? = null,
  val preSentenceActivity: Boolean = false,
  val awaitingPsr: Boolean = false,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OffenderAlias(
  val dateOfBirth: LocalDate? = null,
  val firstName: String? = null,
  val middleNames: List<String>? = null,
  val surname: String? = null,
  val gender: String? = null,
)

data class SearchResponses(val searchResponses: List<SearchResponse> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchResponse(
  val offenderId: Long? = null,
  val otherIds: OtherIds? = null,
  @param:JsonProperty("probationStatus") @get:JsonProperty("probationStatus")
  val probationStatusDetail: ProbationStatusDetail? = null,
)

internal data class SearchRequest(val crn: String)
