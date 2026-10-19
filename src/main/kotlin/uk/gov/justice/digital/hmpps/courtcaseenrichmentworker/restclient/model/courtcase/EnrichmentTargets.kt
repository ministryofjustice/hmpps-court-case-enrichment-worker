package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase

data class DefendantIdsPage(
  val defendantIds: List<String>,
  val page: Int,
  val size: Int,
  val totalElements: Long,
  val totalPages: Int,
  val hasNext: Boolean,
)

data class CidsPage(
  val cIds: List<String>,
  val page: Int,
  val size: Int,
  val totalElements: Long,
  val totalPages: Int,
  val hasNext: Boolean,
)
