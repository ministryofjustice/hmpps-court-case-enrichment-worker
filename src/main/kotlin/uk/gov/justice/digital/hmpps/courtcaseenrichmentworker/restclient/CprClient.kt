package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprDefendant

interface CprClient {
  fun getCommonPlatformPerson(defendantId: String): Mono<CprDefendant>
  fun getLibraPerson(cId: String): Mono<CprDefendant>
}
