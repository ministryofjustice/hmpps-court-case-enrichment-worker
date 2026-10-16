package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.api.model

data class WorkerEnrichmentResponse(
  val sourceType: String,
  val targetId: String,
  val status: String,
  val offender: WorkerOffenderDetails? = null,
  val probation: WorkerProbationDetails? = null,
  val warnings: List<String> = emptyList(),
  val errors: List<String> = emptyList(),
)

data class WorkerOffenderDetails(
  val crn: String? = null,
  val pnc: String? = null,
  val cro: String? = null,
)

data class WorkerProbationDetails(
  val probationStatus: String? = null,
  val awaitingPsr: Boolean? = null,
  val breach: Boolean? = null,
)
