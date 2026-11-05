package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.CidsPage
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.DefendantIdsPage
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistEnrichmentResultRequest
import java.time.Duration
import java.time.LocalDate

@Component
class CourtCaseServiceClient(
  @param:Qualifier("courtCaseServiceWebClient") private val webClient: WebClient,
  @param:Value("\${court-case-service.timeout:10s}") private val timeout: Duration,
  @param:Value("\${court-case-service.post-enrichment-url:/enrichment-results}") private val postEnrichmentUrl: String = "/enrichment-results",
) {
  fun persistEnrichmentResult(body: PersistEnrichmentResultRequest): Mono<Void> = Mono.defer {
    require(body.targetId.isNotBlank()) { "targetId must not be blank" }
    require(body.enrichedBy.isNotBlank()) { "enrichedBy must not be blank" }
    require(body.jobRunId == null || body.jobRunId.isNotBlank()) { "jobRunId must not be blank when supplied" }
    require(postEnrichmentUrl.startsWith("/") && !postEnrichmentUrl.startsWith("//")) {
      "post-enrichment-url must be a CCS-relative path"
    }

    webClient.post()
      .uri { builder -> builder.path(postEnrichmentUrl).build() }
      .contentType(MediaType.APPLICATION_JSON)
      .attributes(clientRegistrationId("court-case-service-client"))
      .bodyValue(body)
      .exchangeToMono { response ->
        if (response.statusCode().is2xxSuccessful) {
          response.releaseBody()
        } else {
          response.createException().flatMap {
            logPersistenceFailure(body, it)
            Mono.error<Void>(it)
          }
        }
      }
  }
    .timeout(timeout)

  private fun logPersistenceFailure(body: PersistEnrichmentResultRequest, error: WebClientResponseException) {
    val responseText = error.responseBodyAsString.replace("\\\"", "\"")
    log.warn(
      "CCS persistence rejected runId={} sourceType={} enrichmentStatus={} httpStatus={} responseBytes={} responseErrorHints={} responseFieldHints={}",
      body.jobRunId,
      body.sourceType,
      body.status,
      error.statusCode.value(),
      error.responseBodyAsByteArray.size,
      ERROR_HINTS.filter { responseText.contains(it, ignoreCase = true) },
      FIELD_HINTS.filter { responseText.contains("\"$it\"") },
    )
  }

  fun getDefendantIds(
    fromDate: LocalDate,
    toDate: LocalDate,
    courtCode: String? = null,
    page: Int = 0,
    size: Int = 200,
  ): Mono<DefendantIdsPage> = getTargets("/enrichment-targets/defendant-ids", fromDate, toDate, courtCode, page, size, DefendantIdsPage::class.java)

  fun getCids(
    fromDate: LocalDate,
    toDate: LocalDate,
    courtCode: String? = null,
    page: Int = 0,
    size: Int = 200,
  ): Mono<CidsPage> = getTargets("/enrichment-targets/cids", fromDate, toDate, courtCode, page, size, CidsPage::class.java)

  private fun <T : Any> getTargets(
    path: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    courtCode: String?,
    page: Int,
    size: Int,
    responseType: Class<T>,
  ): Mono<T> = Mono.defer {
    require(fromDate.isBefore(toDate)) { "fromDate must be before the exclusive toDate" }
    require(page >= 0) { "page must be zero-based and non-negative" }
    require(size in 1..500) { "size must be between 1 and 500" }
    require(courtCode == null || courtCode.isNotBlank()) { "courtCode must not be blank when supplied" }

    webClient.get()
      .uri { builder ->
        builder.path(path)
          .queryParam("fromDate", fromDate)
          .queryParam("toDate", toDate)
          .queryParam("page", page)
          .queryParam("size", size)
        if (courtCode != null) builder.queryParam("courtCode", courtCode)
        builder.build()
      }
      .accept(MediaType.APPLICATION_JSON)
      .attributes(clientRegistrationId("court-case-service-client"))
      .retrieve()
      .bodyToMono(responseType)
      .switchIfEmpty(Mono.error(IllegalStateException("Court Case Service returned an empty response")))
  }
    .timeout(timeout)
    .retryWhen(serviceClientRetry("Court Case Service"))

  companion object {
    private val log = LoggerFactory.getLogger(CourtCaseServiceClient::class.java)

    // Emit only fixed hints: downstream messages can include rejected personal data.
    private val ERROR_HINTS = listOf(
      "HttpMessageNotReadableException",
      "InvalidFormatException",
      "MismatchedInputException",
      "MissingKotlinParameterException",
      "UnrecognizedPropertyException",
      "MethodArgumentNotValidException",
      "ConstraintViolationException",
      "JSON parse error",
      "Cannot deserialize",
      "Unrecognized field",
      "Missing required",
      "must not be null",
      "must not be blank",
      "must not be empty",
    )
    private val FIELD_HINTS = listOf(
      "sourceType", "targetId", "status", "defendant", "offender", "probation",
      "groupedOffenderMatches", "warnings", "errors", "enrichedBy", "jobRunId",
      "cprUUID", "name", "dateOfBirth", "sex", "address", "crn", "pnc", "cro",
      "probationStatus", "awaitingPsr", "breach", "previouslyKnownTerminationDate",
      "preSentenceActivity", "matches", "matchIdentifiers", "matchType", "confirmed", "rejected",
    )
  }
}
