package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "defendant-enrichment")
class DefendantEnrichmentProperties {
  var courtCodes: List<String> = emptyList()
}
