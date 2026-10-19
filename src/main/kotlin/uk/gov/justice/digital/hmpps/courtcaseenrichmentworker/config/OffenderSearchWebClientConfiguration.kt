package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class OffenderSearchWebClientConfiguration(
  @param:Value("\${offender-search.base-url}") private val baseUrl: String,
  private val clientRegistrationRepository: ClientRegistrationRepository,
  private val authorizedClientService: OAuth2AuthorizedClientService,
) {
  @Bean
  fun offenderSearchWebClient(builder: WebClient.Builder): WebClient = serviceOAuthWebClient(
    builder,
    baseUrl,
    "offender-search-client",
    clientRegistrationRepository,
    authorizedClientService,
  )
}
