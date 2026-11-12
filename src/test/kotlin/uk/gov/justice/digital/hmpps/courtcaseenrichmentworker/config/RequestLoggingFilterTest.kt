package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.servlet.ServletException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RequestLoggingFilterTest {
  private val logger = LoggerFactory.getLogger(RequestLoggingFilter::class.java) as Logger
  private val appender = ListAppender<ILoggingEvent>()
  private val filter = RequestLoggingFilter()
  private var originalLevel: Level? = null
  private var originalRequestId: String? = null

  @BeforeEach
  fun setUp() {
    originalLevel = logger.level
    originalRequestId = MDC.get("requestId")
    MDC.remove("requestId")
    logger.level = Level.INFO
    appender.start()
    logger.addAppender(appender)
  }

  @AfterEach
  fun tearDown() {
    logger.level = originalLevel
    logger.detachAppender(appender)
    appender.stop()
    if (originalRequestId == null) MDC.remove("requestId") else MDC.put("requestId", originalRequestId)
  }

  @Test
  fun `logs method path and timing without query string headers or bodies`() {
    val request = MockHttpServletRequest("GET", "/enrichment/libra/abc123").apply {
      queryString = "name=sensitive-name"
      addHeader("Authorization", "Bearer sensitive-token")
      setContent("sensitive-request".toByteArray())
    }
    val response = MockHttpServletResponse()
    var requestId: String? = null

    filter.doFilter(request, response) { _, _ ->
      requestId = MDC.get("requestId")
      response.writer.write("sensitive-response")
    }

    val messages = appender.list.map { it.formattedMessage }
    assertThat(messages).hasSize(2)
    assertThat(requestId).isNotBlank()
    assertThat(messages[0]).isEqualTo("Incoming request method=GET path=/enrichment/libra/abc123")
    assertThat(messages[1]).contains("Completed request method=GET path=/enrichment/libra/abc123", "httpStatus=200", "elapsedMs=")
    assertThat(appender.list.map { it.mdcPropertyMap["requestId"] }).containsOnly(requestId)
    assertThat(messages.joinToString()).doesNotContain("sensitive")
    assertThat(response.contentAsString).isEqualTo("sensitive-response")
    assertThat(MDC.get("requestId")).isNull()
  }

  @ParameterizedTest
  @CsvSource("200, INFO", "401, WARN", "403, WARN", "404, WARN", "500, ERROR", "503, ERROR")
  fun `logs response severity by status`(status: Int, level: String) {
    val response = MockHttpServletResponse()
    filter.doFilter(MockHttpServletRequest("POST", "/unknown"), response) { _, _ ->
      response.status = status
    }

    assertThat(appender.list.last().level).isEqualTo(Level.valueOf(level))
    assertThat(appender.list.last().formattedMessage).contains("path=/unknown", "httpStatus=$status")
    assertThat(response.status).isEqualTo(status)
  }

  @Test
  fun `propagates unhandled failure and restores previous MDC`() {
    MDC.put("requestId", "previous")
    val error = ServletException("sensitive-message")
    assertSame(
      error,
      assertThrows(ServletException::class.java) {
        filter.doFilter(MockHttpServletRequest("POST", "/jobs/enrich-defendants"), MockHttpServletResponse()) { _, _ -> throw error }
      },
    )

    assertThat(appender.list.last().level).isEqualTo(Level.ERROR)
    assertThat(appender.list.last().formattedMessage).contains("httpStatus=500").doesNotContain("sensitive-message")
    assertThat(appender.list.last().throwableProxy).isNull()
    assertThat(MDC.get("requestId")).isEqualTo("previous")
  }

  @ParameterizedTest
  @ValueSource(strings = ["/health", "/health/liveness", "/health/readiness", "/info", "/ping"])
  fun `does not log routine probe requests`(path: String) {
    var invoked = false
    filter.doFilter(MockHttpServletRequest("GET", path), MockHttpServletResponse()) { _, _ -> invoked = true }

    assertThat(invoked).isTrue()
    assertThat(appender.list).isEmpty()
  }
}
