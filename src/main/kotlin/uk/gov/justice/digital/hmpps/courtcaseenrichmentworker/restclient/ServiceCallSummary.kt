package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

internal enum class EnrichmentConnector {
  CPR,
  OFFENDER_SEARCH,
}

internal class ServiceCallSummary(private val runId: String) {
  private data class Counts(
    var calls: Long = 0,
    var completed: Long = 0,
    var emptyResults: Long = 0,
    var failures: Long = 0,
    var elapsedNanos: Long = 0,
  )

  private val counts = EnrichmentConnector.entries.associateWith { Counts() }

  @Synchronized
  fun record(connector: EnrichmentConnector, empty: Boolean, failed: Boolean, elapsedNanos: Long) {
    counts.getValue(connector).apply {
      calls++
      if (failed) failures++ else completed++
      if (!failed && empty) emptyResults++
      this.elapsedNanos += elapsedNanos
    }
  }

  fun <T> collecting(action: () -> T): T {
    val previous = current.get()
    current.set(this)
    try {
      return action()
    } finally {
      if (previous == null) current.remove() else current.set(previous)
      logSummaries()
    }
  }

  @Synchronized
  private fun logSummaries() {
    counts.forEach { (connector, totals) ->
      log.info(
        "Connector summary runId={} connector={} calls={} completed={} emptyResults={} failures={} elapsedMs={}",
        runId,
        connector,
        totals.calls,
        totals.completed,
        totals.emptyResults,
        totals.failures,
        TimeUnit.NANOSECONDS.toMillis(totals.elapsedNanos),
      )
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger("ServiceClientLogging")

    // The job orchestrates synchronously; clients capture this scope before switching to Reactor threads.
    val current = ThreadLocal<ServiceCallSummary>()
  }
}
