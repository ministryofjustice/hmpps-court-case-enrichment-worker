package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.constraints.NotBlank
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentResult
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service.DefendantEnrichmentService

@RestController
@PreAuthorize("isAuthenticated()")
@ApiResponse(responseCode = "200", description = "Enrichment result, including NO_MATCH or NEEDS_REVIEW")
@ApiResponse(responseCode = "400", description = "Identifier must not be blank")
@ApiResponse(responseCode = "401", description = "A valid bearer token is required")
@ApiResponse(responseCode = "500", description = "Enrichment failed; no result should be applied")
class DefendantEnrichmentResource(
  private val defendantEnrichmentService: DefendantEnrichmentService,
) {
  @GetMapping("/enrichment/common-platform/{defendantId}", produces = [MediaType.APPLICATION_JSON_VALUE])
  @Operation(
    summary = "Enrich a Common Platform defendant by defendant ID",
    description = "Looks up CPR and Offender Search synchronously and returns a clean enrichment result. " +
      "Does not fetch CCS targets or persist any changes.",
    security = [SecurityRequirement(name = "bearerAuth")],
  )
  fun getCommonPlatformEnrichment(@PathVariable @NotBlank defendantId: String): EnrichmentResult = defendantEnrichmentService.enrichCommonPlatformDefendant(defendantId)

  @GetMapping("/enrichment/libra/{cId}", produces = [MediaType.APPLICATION_JSON_VALUE])
  @Operation(
    summary = "Enrich a Libra defendant by cID",
    description = "Looks up CPR and Offender Search synchronously and returns a clean enrichment result. " +
      "Does not fetch CCS targets or persist any changes.",
    security = [SecurityRequirement(name = "bearerAuth")],
  )
  fun getLibraEnrichment(@PathVariable @NotBlank cId: String): EnrichmentResult = defendantEnrichmentService.enrichLibraDefendant(cId)
}
