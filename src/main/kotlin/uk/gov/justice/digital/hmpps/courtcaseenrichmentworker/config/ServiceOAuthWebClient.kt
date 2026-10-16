package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

internal fun serviceOAuthWebClient(
  builder: WebClient.Builder,
  baseUrl: String,
  registrationId: String,
  registrations: ClientRegistrationRepository,
  clients: OAuth2AuthorizedClientService,
): WebClient {
  val manager = AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients)
  manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build())
  val oauth = ServletOAuth2AuthorizedClientExchangeFilterFunction(manager)
  val principal = UsernamePasswordAuthenticationToken("court-case-enrichment-worker", null, emptyList())
  val registrationAttributes = mutableMapOf<String, Any>()
  ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId(registrationId).accept(registrationAttributes)
  return builder.baseUrl(baseUrl)
    // Spring's servlet filter cannot acquire a token without a servlet request.
    .filter { request, next ->
      if (!request.attributes().entries.containsAll(registrationAttributes.entries)) {
        next.exchange(request)
      } else {
        Mono.fromCallable {
          manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(registrationId).principal(principal).build())
        }
          .subscribeOn(Schedulers.boundedElastic())
          .switchIfEmpty(Mono.error(IllegalStateException("Unable to authorize $registrationId")))
          .flatMap { client ->
            next.exchange(
              ClientRequest.from(request)
                .attributes(ServletOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient(client))
                .attributes(ServletOAuth2AuthorizedClientExchangeFilterFunction.authentication(principal))
                .build(),
            )
          }
      }
    }
    .filter(oauth)
    .build()
}
