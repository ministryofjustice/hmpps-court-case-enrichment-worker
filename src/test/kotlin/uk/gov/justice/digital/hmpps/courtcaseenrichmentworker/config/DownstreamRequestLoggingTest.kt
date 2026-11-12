package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config

import ch.qos.logback.classic.Level
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
import org.slf4j.MDC
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import java.net.URI

class DownstreamRequestLoggingTest {
  private val logger = LoggerFactory.getLogger("DownstreamRequestLogging") as Logger
  private val appender = ListAppender<ILoggingEvent>()
  private var originalLevel: Level? = null
  private val request = ClientRequest.create(HttpMethod.POST, URI("https://service/person/sensitive-id?name=sensitive-name"))
    .header("Authorization", "Bearer sensitive-token")
    .body(BodyInserters.fromValue("sensitive-request"))
    .build()

  @BeforeEach
  fun setUp() {
    originalLevel = logger.level
    logger.level = Level.DEBUG
    appender.start()
    logger.addAppender(appender)
  }

  @AfterEach
  fun tearDown() {
    logger.level = originalLevel
    logger.detachAppender(appender)
    appender.stop()
  }

  @Test
  fun `logs each subscribed exchange with correlation and leaves response body untouched`() {
    val response = ClientResponse.create(HttpStatus.OK).body("sensitive-response").build()
    val previousRequestId = MDC.get("requestId")
    val call = try {
      MDC.put("requestId", "incoming-id")
      downstreamRequestLogging("court-case-service-client").filter(
        request,
        ExchangeFunction {
          assertSame(request, it)
          Mono.just(response)
        },
      )
    } finally {
      if (previousRequestId == null) MDC.remove("requestId") else MDC.put("requestId", previousRequestId)
    }
    assertThat(appender.list).isEmpty()

    assertSame(response, call.block())
    assertThat(response.bodyToMono(String::class.java).block()).isEqualTo("sensitive-response")
    val messages = appender.list.map { it.formattedMessage }
    assertThat(messages).hasSize(2)
    assertThat(messages[0]).contains("Outgoing request", "service=court-case-service-client", "method=POST", "requestId=incoming-id")
    assertThat(messages[1]).contains("Received downstream response", "httpStatus=200", "elapsedMs=", "requestId=incoming-id")
    val callId = messages[0].substringAfter("callId=").substringBefore(" ")
    assertThat(messages[1]).contains("callId=$callId")
    assertThat(messages.joinToString()).doesNotContain("sensitive", "https://")
  }

  @Test
  fun `logs transport failures without exception messages and preserves error`() {
    val error = IllegalStateException("sensitive-failure")
    val call = downstreamRequestLogging("service").filter(request, ExchangeFunction { Mono.error(error) })

    assertSame(error, assertThrows(IllegalStateException::class.java) { call.block() })

    assertThat(appender.list.last().level).isEqualTo(Level.WARN)
    assertThat(appender.list.last().formattedMessage).contains("Downstream request failed", "errorType=IllegalStateException")
      .doesNotContain("sensitive")
    assertThat(appender.list.last().throwableProxy).isNull()
  }

  @Test
  fun `records HTTP error status without treating the response as a transport failure`() {
    val response = ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("sensitive-error").build()
    assertSame(response, downstreamRequestLogging("service").filter(request, ExchangeFunction { Mono.just(response) }).block())

    assertThat(appender.list.last().formattedMessage).contains("httpStatus=503").doesNotContain("sensitive")
    assertThat(appender.list).hasSize(2)
  }
}
