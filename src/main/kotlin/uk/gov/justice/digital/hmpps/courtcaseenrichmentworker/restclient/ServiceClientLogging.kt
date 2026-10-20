package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.TimeUnit

internal fun <T : Any> logServiceCall(
  operation: String,
  call: () -> Mono<T>,
  outcome: (T?) -> String,
): Mono<T> = Mono.defer {
  val log = LoggerFactory.getLogger("ServiceClientLogging")
  val callId = UUID.randomUUID()
  val started = System.nanoTime()
  log.info("Starting downstream call operation={} callId={}", operation, callId)
  Mono.defer(call)
    .doOnSuccess { result ->
      log.info(
        "Completed downstream call operation={} callId={} elapsedMs={} outcome={}",
        operation,
        callId,
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
        outcome(result),
      )
    }
    .doOnError { error ->
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
