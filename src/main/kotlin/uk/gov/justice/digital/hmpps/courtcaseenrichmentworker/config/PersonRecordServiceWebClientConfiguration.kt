package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import java.time.Duration

@Configuration
class PersonRecordServiceWebClientConfiguration(
  @param:Value("\${person-record-service.base-url}") private val baseUrl: String,
  @param:Value("\${person-record-service.timeout}") private val timeout: Duration,
) {
  @Bean
  fun personRecordServiceWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    registrationId = "person-record-search-client",
    url = baseUrl,
    timeout = timeout,
  )
}
