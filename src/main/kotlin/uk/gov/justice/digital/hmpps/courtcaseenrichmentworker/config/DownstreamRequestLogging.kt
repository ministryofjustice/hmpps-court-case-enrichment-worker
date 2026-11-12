package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.TimeUnit

internal fun downstreamRequestLogging(service: String): ExchangeFilterFunction = ExchangeFilterFunction { request, next ->
  val requestId = MDC.get("requestId")
  Mono.defer {
    val log = LoggerFactory.getLogger("DownstreamRequestLogging")
    val callId = UUID.randomUUID()
    val started = System.nanoTime()
    log.debug("Outgoing request service={} method={} callId={} requestId={}", service, request.method(), callId, requestId)
    Mono.defer { next.exchange(request) }
      .doOnNext { response ->
        log.debug(
          "Received downstream response service={} method={} callId={} requestId={} httpStatus={} elapsedMs={}",
          service,
          request.method(),
          callId,
          requestId,
          response.statusCode().value(),
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
        )
      }
      .doOnError { error ->
        log.warn(
          "Downstream request failed service={} method={} callId={} requestId={} errorType={} elapsedMs={}",
          service,
          request.method(),
          callId,
          requestId,
          error.javaClass.simpleName,
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
        )
      }
  }
}
