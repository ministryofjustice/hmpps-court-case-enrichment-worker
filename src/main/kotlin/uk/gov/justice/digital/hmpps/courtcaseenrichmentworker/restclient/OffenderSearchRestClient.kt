package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.MatchRequest
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.MatchResponse
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.SearchRequest
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.SearchResponse
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.SearchResponses
import java.time.Duration

@Component
class OffenderSearchRestClient(
  @param:Qualifier("offenderSearchWebClient") private val webClient: WebClient,
  @param:Value("\${offender-search.post-match-url:/match}") private val postMatchUrl: String,
  @param:Value("\${offender-search.post-search-url:/search}") private val postSearchUrl: String,
  @param:Value("\${offender-search.disable-authentication:false}") private val disableAuthentication: Boolean,
  @param:Value("\${offender-search.timeout:10s}") private val timeout: Duration,
) {
  fun match(body: MatchRequest): Mono<MatchResponse> = logServiceCall(
    operation = "Offender Search POST match",
    call = { requestMatch(body) },
    outcome = { "MATCHES_RECEIVED count=${it?.matchCount ?: 0}" },
  )

  private fun requestMatch(body: MatchRequest): Mono<MatchResponse> = Mono.defer {
    require(body.surname.isNotBlank()) { "No surname provided" }
    post(postMatchUrl).bodyValue(body).retrieve().bodyToMono(MatchResponse::class.java)
      .switchIfEmpty(Mono.error(IllegalStateException("Offender Search returned an empty match response")))
  }
    .timeout(timeout)
    .retryWhen(serviceClientRetry("Offender Search match"))

  fun search(crn: String): Mono<SearchResponses> = logServiceCall(
    operation = "Offender Search POST search",
    call = { requestSearch(crn) },
    outcome = { "SEARCH_RESULTS_RECEIVED count=${it?.searchResponses?.size ?: 0}" },
  )

  private fun requestSearch(crn: String): Mono<SearchResponses> = Mono.defer {
    require(crn.isNotBlank()) { "CRN must not be blank" }
    post(postSearchUrl).bodyValue(SearchRequest(crn)).retrieve()
      .bodyToMono(object : ParameterizedTypeReference<List<SearchResponse>>() {})
      .switchIfEmpty(Mono.error(IllegalStateException("Offender Search returned an empty search response")))
      .map { SearchResponses(it) }
  }
    .timeout(timeout)
    .retryWhen(serviceClientRetry("Offender Search search"))

  private fun post(path: String): WebClient.RequestBodySpec {
    val request = webClient.post().uri(path).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
    return if (disableAuthentication) request else request.attributes(clientRegistrationId("offender-search-client"))
  }
}
