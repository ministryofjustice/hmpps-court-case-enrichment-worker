package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.batch

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service.DefendantEnrichmentJobService

enum class BatchType {
  ENRICH_DEFENDANTS,
}

@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
@Service
class BatchManager(
  @param:Value("\${batch.type}") private val batchType: BatchType,
  private val defendantEnrichmentJobService: DefendantEnrichmentJobService,
) {
  companion object {
    private val LOG = LoggerFactory.getLogger(this::class.java)
  }

  @EventListener
  fun onApplicationEvent(event: ContextRefreshedEvent) {
    try {
      runBatchJob(batchType)
    } finally {
      (event.applicationContext as ConfigurableApplicationContext).close()
    }
  }

  fun runBatchJob(batchType: BatchType) {
    LOG.info("Running batch job type {}", batchType)
    when (batchType) {
      BatchType.ENRICH_DEFENDANTS -> defendantEnrichmentJobService.enrichDefendants()
    }
    LOG.info("Finished batch job type {}", batchType)
  }
}
