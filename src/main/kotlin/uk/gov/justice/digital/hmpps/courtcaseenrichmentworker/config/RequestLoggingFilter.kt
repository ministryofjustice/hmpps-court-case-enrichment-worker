package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.event.Level
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : OncePerRequestFilter() {
  override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI.removePrefix(request.contextPath).let {
    it == "/health" || it.startsWith("/health/") || it == "/info" || it == "/ping"
  }

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
    val requestId = UUID.randomUUID().toString()
    val previousRequestId = MDC.get("requestId")
    val started = System.nanoTime()
    var completed = false
    MDC.put("requestId", requestId)
    try {
      log.info("Incoming request method={} path={}", request.method, request.requestURI)
      filterChain.doFilter(request, response)
      completed = true
    } finally {
      val status = if (completed) response.status else HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      val level = when {
        status >= 500 -> Level.ERROR
        status >= 400 -> Level.WARN
        else -> Level.INFO
      }
      log.atLevel(level).log(
        "Completed request method={} path={} httpStatus={} elapsedMs={}",
        request.method,
        request.requestURI,
        status,
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
      )
      if (previousRequestId == null) MDC.remove("requestId") else MDC.put("requestId", previousRequestId)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(RequestLoggingFilter::class.java)
  }
}
