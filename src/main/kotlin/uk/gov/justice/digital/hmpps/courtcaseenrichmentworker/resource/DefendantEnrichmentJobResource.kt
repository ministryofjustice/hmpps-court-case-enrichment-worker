package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
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
  @PreAuthorize("isAuthenticated()")
  @Operation(
    summary = "Trigger defendant enrichment from a Kubernetes CronJob",
    description = "Fetches Common Platform defendant IDs and Libra cIDs from CCS, " +
      "enriches each target synchronously using CPR and Offender Search, and returns clean results. " +
      "Temporarily restricted to court B03DE for the POC; no court-code parameter is accepted. " +
      "Does not yet persist results to CCS.",
    security = [SecurityRequirement(name = "bearerAuth")],
  )
  @ApiResponse(responseCode = "200", description = "Defendant enrichment completed without persistence")
  fun enrichDefendants(): DefendantEnrichmentJobResult = defendantEnrichmentJobService.enrichDefendants()
}
