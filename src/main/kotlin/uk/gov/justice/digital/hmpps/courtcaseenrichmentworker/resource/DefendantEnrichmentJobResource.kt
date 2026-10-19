package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.resource.model.DefendantEnrichmentJobResult
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service.DefendantEnrichmentJobService

@RestController
class DefendantEnrichmentJobResource(
  private val defendantEnrichmentJobService: DefendantEnrichmentJobService,
) {
  @PostMapping("/jobs/enrich-defendants", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("permitAll()")
  @SecurityRequirements
  @Operation(
    summary = "Trigger defendant enrichment from a Kubernetes CronJob",
    description = "Fetches Common Platform defendant IDs and Libra cIDs from CCS, " +
      "across all courts; no court-code parameter is accepted. " +
      "No authentication is required; access must be restricted by network controls. " +
      "Enrichment is temporarily disabled: no CPR, Offender Search, or persistence calls are made.",
  )
  @ApiResponse(responseCode = "200", description = "Enrichment targets fetched without processing or persistence")
  fun enrichDefendants(): DefendantEnrichmentJobResult = defendantEnrichmentJobService.enrichDefendants()
}
