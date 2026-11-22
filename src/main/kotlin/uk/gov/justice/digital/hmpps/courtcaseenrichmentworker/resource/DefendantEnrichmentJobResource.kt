package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.resource

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.resource.model.DefendantEnrichmentJobResult
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service.DefendantEnrichmentJobService

@RestController
@Hidden
class DefendantEnrichmentJobResource(
  private val defendantEnrichmentJobService: DefendantEnrichmentJobService,
) {
  @PostMapping("/jobs/enrich-defendants", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("permitAll()")
  @SecurityRequirements
  fun enrichDefendants(): DefendantEnrichmentJobResult = defendantEnrichmentJobService.enrichDefendants()
}
