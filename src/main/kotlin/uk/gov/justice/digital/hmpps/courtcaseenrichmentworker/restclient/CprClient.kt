package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprDefendant

@Component
class CprClient(
  @param:Qualifier("personRecordServiceWebClient") private val webClient: WebClient,
) {
  fun getCommonPlatformPerson(defendantId: String): Mono<CprDefendant> = getCprRecord(
    "/person/commonplatform/{id}",
    defendantId,
  )

  fun getLibraPerson(cId: String): Mono<CprDefendant> = getCprRecord(
    "/person/libra/{id}",
    cId,
  )

  private fun getCprRecord(
    path: String,
    identifier: String,
  ): Mono<CprDefendant> = logServiceCall(
    operation = "CPR GET $path",
    connector = EnrichmentConnector.CPR,
    call = { requestCprRecord(path, identifier) },
    outcome = { if (it == null) "NOT_FOUND" else "RECORD_RECEIVED" },
  )

  private fun requestCprRecord(path: String, identifier: String): Mono<CprDefendant> = webClient
    .get()
    .uri(path, identifier)
    .accept(APPLICATION_JSON)
    .exchangeToMono { response ->
      when {
        response.statusCode().is2xxSuccessful -> response.bodyToMono<CprDefendant>()
          .switchIfEmpty(Mono.error(IllegalStateException("CPR service returned an empty successful response")))
        response.statusCode().value() == 404 -> response.releaseBody().then(Mono.empty())
        else -> response.createException().flatMap { Mono.error(it) }
      }
    }
    .retryWhen(serviceClientRetry("CPR service"))
}
