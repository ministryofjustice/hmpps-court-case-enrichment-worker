package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedAddress
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedAlias
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedDefendant
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedGroupedOffenderMatches
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedMatchIdentifiers
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedName
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedOffender
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedOffenderMatch
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedProbation
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentResult
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentSourceType
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentStatus
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.toPersistenceRequest
import java.time.LocalDate

class EnrichmentResultMappingTest {
  private val logger = LoggerFactory.getLogger("EnrichmentResultMapping") as Logger
  private val appender = ListAppender<ILoggingEvent>()
  private var originalLevel: Level? = null

  @BeforeEach
  fun attachAppender() {
    originalLevel = logger.level
    logger.level = Level.ERROR
    appender.start()
    logger.addAppender(appender)
  }

  @AfterEach
  fun detachAppender() {
    logger.level = originalLevel
    logger.detachAppender(appender)
    appender.stop()
  }

  @Test
  fun `maps every clean result field without losing nulls flags dates or aliases`() {
    val result = EnrichmentResult(
      sourceType = EnrichmentSourceType.LIBRA,
      targetId = "libra",
      status = EnrichmentStatus.NEEDS_REVIEW,
      defendant = EnrichedDefendant(
        "record",
        EnrichedName("Ms", "First", "Middle", "Last"),
        LocalDate.of(1990, 1, 2),
        "Female",
        EnrichedAddress("one", "two", "three", "four", "five", "postcode"),
      ),
      offender = EnrichedOffender("crn", "pnc", "cro"),
      probation = EnrichedProbation("PREVIOUSLY_KNOWN", false, true, LocalDate.of(2025, 2, 3), true),
      groupedOffenderMatches = EnrichedGroupedOffenderMatches(
        listOf(
          EnrichedOffenderMatch(
            EnrichedMatchIdentifiers("crn", "pnc", "cro", listOf(EnrichedAlias("Alias", "Surname", listOf("Middle")))),
            confirmed = true,
            rejected = true,
          ),
        ),
      ),
      warnings = listOf("Review required"),
      errors = listOf("Example error"),
    )

    val request = result.toPersistenceRequest("run")

    assertThat(request).usingRecursiveComparison()
      .ignoringFields("enrichedBy", "jobRunId", "defendant.sex")
      .isEqualTo(result)
    assertThat(request.defendant?.sex).isEqualTo("FEMALE")
    assertThat(request.enrichedBy).isEqualTo("court-case-enrichment-worker")
    assertThat(request.jobRunId).isEqualTo("run")
  }

  @Test
  fun `no match retains absent details rather than inventing enrichment`() {
    val result = EnrichmentResult(EnrichmentSourceType.COMMON_PLATFORM, "cp", EnrichmentStatus.NO_MATCH)

    assertThat(result.toPersistenceRequest("run")).usingRecursiveComparison()
      .ignoringFields("enrichedBy", "jobRunId")
      .isEqualTo(result)
  }

  @ParameterizedTest
  @CsvSource(
    "Male,MALE", "male,MALE", "M,MALE",
    "Female,FEMALE", "female,FEMALE", "f,FEMALE",
    "Not Known,NOT_KNOWN", "NOT_KNOWN,NOT_KNOWN", "n,NOT_KNOWN",
    "Not Specified,NOT_SPECIFIED", "NOT_SPECIFIED,NOT_SPECIFIED", "ns,NOT_SPECIFIED",
    "'  Not known  ',NOT_KNOWN",
  )
  fun `maps recognised descriptions and codes to CCS enum names`(sex: String, expected: String) {
    val result = resultWithSex(sex)

    val request = result.toPersistenceRequest("run")

    assertThat(request.defendant?.sex).isEqualTo(expected)
    assertThat(expected).matches("(?i)MALE|M|FEMALE|F|NOT_KNOWN|N|NOT_SPECIFIED|NS")
    assertThat(result.defendant?.sex).isEqualTo(sex)
    assertThat(appender.list).isEmpty()
  }

  @ParameterizedTest
  @NullSource
  fun `preserves an absent sex`(sex: String?) {
    assertThat(resultWithSex(sex).toPersistenceRequest("run").defendant?.sex).isNull()
    assertThat(appender.list).isEmpty()
  }

  @ParameterizedTest
  @ValueSource(strings = ["", " ", "sensitive-unrecognised-value", "sensitive\r\nforged-log-entry"])
  fun `maps unsupported sex to not known and logs without exposing the value`(sex: String) {
    val result = resultWithSex(sex)

    assertThat(result.toPersistenceRequest("run").defendant?.sex).isEqualTo("NOT_KNOWN")
    assertThat(result.defendant?.sex).isEqualTo(sex)
    assertThat(appender.list).hasSize(1)
    assertThat(appender.list.single().level).isEqualTo(Level.ERROR)
    assertThat(appender.list.single().formattedMessage)
      .isEqualTo("Unrecognised defendant.sex mapped to NOT_KNOWN runId=run sourceType=COMMON_PLATFORM")
    assertThat(appender.list.single().throwableProxy).isNull()
  }

  private fun resultWithSex(sex: String?) = EnrichmentResult(
    EnrichmentSourceType.COMMON_PLATFORM,
    "cp",
    EnrichmentStatus.ENRICHED,
    defendant = EnrichedDefendant(name = EnrichedName(null, "First", null, "Last"), sex = sex),
  )
}
