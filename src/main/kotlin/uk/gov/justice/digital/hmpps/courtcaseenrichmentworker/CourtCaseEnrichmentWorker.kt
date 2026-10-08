package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CourtCaseEnrichmentWorker

fun main(args: Array<String>) {
  runApplication<CourtCaseEnrichmentWorker>(*args)
}
