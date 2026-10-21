package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.TimeUnit

internal fun <T : Any> logServiceCall(
  operation: String,
  call: () -> Mono<T>,
  connector: EnrichmentConnector = EnrichmentConnector.CPR,
  emptyResult: (T?) -> Boolean = { it == null },
  outcome: (T?) -> String,
): Mono<T> {
  val summary = ServiceCallSummary.current.get()
  return Mono.defer {
    val log = LoggerFactory.getLogger("ServiceClientLogging")
    val callId = UUID.randomUUID()
    val started = System.nanoTime()
    log.debug("Starting downstream call operation={} callId={}", operation, callId)
    Mono.defer(call)
      .doOnSuccess { result ->
        summary?.record(connector, emptyResult(result), false, System.nanoTime() - started)
        log.debug(
          "Completed downstream call operation={} callId={} elapsedMs={} outcome={}",
          operation,
          callId,
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
          outcome(result),
        )
      }
      .doOnError { error ->
        summary?.record(connector, false, true, System.nanoTime() - started)
        log.warn(
          "Failed downstream call operation={} callId={} elapsedMs={} errorType={} httpStatus={}",
          operation,
          callId,
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
          error.javaClass.simpleName,
          (error as? WebClientResponseException)?.statusCode?.value(),
        )
      }
  }
}
