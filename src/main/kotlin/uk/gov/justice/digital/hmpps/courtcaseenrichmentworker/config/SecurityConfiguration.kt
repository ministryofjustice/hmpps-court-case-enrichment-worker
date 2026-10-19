package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import uk.gov.justice.hmpps.kotlin.auth.dsl.ResourceServerConfigurationCustomizer
import uk.gov.justice.hmpps.kotlin.auth.dsl.UnauthorizedRequestPathsCustomizerBuilder

@Configuration
class SecurityConfiguration {
  @Bean
  fun resourceServerConfigurationCustomizer() = ResourceServerConfigurationCustomizer {
    authorizeHttpRequests {
      authorize(HttpMethod.POST, "/jobs/enrich-defendants", permitAll)
      UnauthorizedRequestPathsCustomizerBuilder().build().unauthorizedRequestPaths.forEach {
        authorize(it, permitAll)
      }
      authorize(anyRequest, authenticated)
    }
  }
}
