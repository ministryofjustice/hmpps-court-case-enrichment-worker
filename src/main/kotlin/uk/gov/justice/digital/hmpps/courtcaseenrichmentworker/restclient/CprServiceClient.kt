package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprDefendant
import java.time.Duration

@Component
class CprServiceClient(
  @param:Qualifier("personRecordServiceWebClient") private val webClient: WebClient,
  @param:Value("\${person-record-service.disable-authentication:false}") private val disableAuthentication: Boolean,
  @param:Value("\${person-record-service.timeout:10s}") private val timeout: Duration,
) : CprClient {
  override fun getCommonPlatformPerson(defendantId: String): Mono<CprDefendant> = getCprCanonicalRecordByCommonPlatformId(defendantId)

  override fun getLibraPerson(cId: String): Mono<CprDefendant> = getCprCanonicalRecordByLibraId(cId)

  fun getCprCanonicalRecordByCommonPlatformId(
    defendantId: String,
  ): Mono<CprDefendant> = getCprRecord(
    "/person/commonplatform/{id}",
    defendantId,
  )

  fun getCprCanonicalRecordByLibraId(
    cId: String,
  ): Mono<CprDefendant> = getCprRecord(
    "/person/libra/{id}",
    cId,
  )

  private fun getCprRecord(
    path: String,
    identifier: String,
  ): Mono<CprDefendant> = logServiceCall(
    operation = "CPR GET $path",
    call = { requestCprRecord(path, identifier) },
    outcome = { if (it == null) "NOT_FOUND" else "RECORD_RECEIVED" },
  )

  private fun requestCprRecord(path: String, identifier: String): Mono<CprDefendant> = get(path, identifier)
    .exchangeToMono { response ->
      when {
        response.statusCode().is2xxSuccessful -> response.bodyToMono(CprDefendant::class.java)
          .switchIfEmpty(Mono.error(IllegalStateException("CPR service returned an empty successful response")))
        response.statusCode().value() == 404 -> response.releaseBody().then(Mono.empty())
        else -> response.createException().flatMap { Mono.error(it) }
      }
    }
    .timeout(timeout)
    .retryWhen(serviceClientRetry("CPR service"))

  private fun get(path: String, identifier: String): WebClient.RequestHeadersSpec<*> {
    val request =
      webClient
        .get()
        .uri(path, identifier)
        .accept(MediaType.APPLICATION_JSON)

    return if (disableAuthentication) {
      request
    } else {
      request.attributes(
        ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("person-record-search-client"),
      )
    }
  }
}
