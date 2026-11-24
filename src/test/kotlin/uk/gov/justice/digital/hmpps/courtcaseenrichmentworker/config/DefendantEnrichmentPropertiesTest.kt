package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.mock.env.MockEnvironment

class DefendantEnrichmentPropertiesTest {
  @Test
  fun `binds comma-separated court codes from an environment variable`() {
    val environment = MockEnvironment()
    environment.propertySources.addFirst(
      SystemEnvironmentPropertySource(
        "testEnvironment",
        mapOf("DEFENDANT_ENRICHMENT_COURT_CODES" to "B03DE,B06IS,B20BL,B47CL"),
      ),
    )

    val properties = Binder.get(environment)
      .bind("defendant-enrichment", Bindable.of(DefendantEnrichmentProperties::class.java))
      .get()

    assertEquals(listOf("B03DE", "B06IS", "B20BL", "B47CL"), properties.courtCodes)
  }
}
