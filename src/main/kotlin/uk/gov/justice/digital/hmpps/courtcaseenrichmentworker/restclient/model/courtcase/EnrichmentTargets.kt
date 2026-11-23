package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase

data class DefendantIdsResponse(
  val defendantIds: List<String>,
)

data class CidsResponse(
  val cIds: List<String>,
)
