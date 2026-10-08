package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PingResource {
  @GetMapping("/ping", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Confirm the service is responding", security = [SecurityRequirement(name = "bearerAuth")])
  fun ping(): PingResponse = PingResponse(message = "HMPPS Court Case Enrichment Worker is running")
}

data class PingResponse(val message: String)
