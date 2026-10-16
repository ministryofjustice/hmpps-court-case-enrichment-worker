package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.TimeoutException

internal fun serviceClientRetry(serviceName: String): Retry = Retry
  .backoff(3, Duration.ofMillis(200))
  .filter { error ->
    error is WebClientRequestException ||
      error is TimeoutException ||
      (error is WebClientResponseException && error.statusCode.is5xxServerError)
  }
  .doBeforeRetry { retry ->
    LoggerFactory.getLogger("ServiceClientRetry").warn(
      "Retrying {} request after transient failure (retry {}/3): {}",
      serviceName,
      retry.totalRetries() + 1,
      retry.failure().javaClass.simpleName,
    )
  }
  .onRetryExhaustedThrow { _, retry -> retry.failure() }
