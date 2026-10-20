package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

class ServiceClientLoggingTest {
  private val logger = LoggerFactory.getLogger("ServiceClientLogging") as Logger
  private val appender = ListAppender<ILoggingEvent>()

  @BeforeEach
  fun attachAppender() {
    appender.start()
    logger.addAppender(appender)
  }

  @AfterEach
  fun detachAppender() {
    logger.detachAppender(appender)
    appender.stop()
  }

  @Test
  fun `logs only on subscription and preserves result without logging its contents`() {
    val call = logServiceCall("test operation", { Mono.just("sensitive-record") }) { "RECORD_RECEIVED" }
    assertThat(appender.list).isEmpty()

    assertThat(call.block()).isEqualTo("sensitive-record")

    val messages = appender.list.map { it.formattedMessage }
    assertThat(messages).hasSize(2)
    assertThat(messages[0]).contains("Starting downstream call", "operation=test operation", "callId=")
    assertThat(messages[1]).contains("Completed downstream call", "elapsedMs=", "outcome=RECORD_RECEIVED")
    assertThat(messages.joinToString()).doesNotContain("sensitive-record")
  }

  @Test
  fun `logs an expected empty result as not found`() {
    logServiceCall<String>("CPR GET /person/libra/{id}", { Mono.empty() }) {
      if (it == null) "NOT_FOUND" else "RECORD_RECEIVED"
    }.block()

    assertThat(appender.list.last().formattedMessage).contains("outcome=NOT_FOUND")
  }

  @Test
  fun `logs final HTTP failure without response body request URL or exception message`() {
    val error = WebClientResponseException.create(
      403,
      "sensitive-error-message",
      HttpHeaders.EMPTY,
      "sensitive-response-body".toByteArray(),
      null,
    )
    val call = logServiceCall<String>("test operation", { Mono.error(error) }) { "SUCCESS" }

    assertSame(error, assertThrows(WebClientResponseException::class.java) { call.block() })

    assertThat(appender.list).hasSize(2)
    assertThat(appender.list.last().formattedMessage)
      .contains("Failed downstream call", "httpStatus=403", "errorType=Forbidden")
      .doesNotContain("sensitive-error-message", "sensitive-response-body", "SUCCESS")
    assertThat(appender.list.last().throwableProxy).isNull()
  }

  @Test
  fun `logs one completion after retries rather than reporting intermediate failures as final`() {
    var attempts = 0
    logServiceCall(
      "test operation",
      {
        Mono.defer {
          attempts++
          if (attempts == 1) Mono.error(IllegalStateException("temporary")) else Mono.just("result")
        }.retry(1)
      },
    ) { "RECORD_RECEIVED" }.block()

    assertThat(attempts).isEqualTo(2)
    assertThat(appender.list.map { it.formattedMessage }).hasSize(2)
      .noneMatch { it.contains("Failed downstream call") }
  }
}
