package com.knohub.backend.config;

import com.knohub.backend.service.ActiveUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Logs each HTTP request/response with a correlation id, timing, and status-aware level.
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final ActiveUserService activeUserService;

    public RequestLoggingFilter(ActiveUserService activeUserService) {
        this.activeUserService = activeUserService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put("traceId", traceId);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = response.getStatus();
            String method = request.getMethod();
            String path = buildPath(request);
            String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("-");
            String clientIp = resolveClientIp(request);
            activeUserService.recordIp(clientIp);

            String message = String.format("%s %s -> %d (%d ms) ip=%s ua=%s", method, path, status, duration, clientIp, userAgent);
            if (status >= 500) {
                log.error(message);
            } else if (status >= 400) {
                log.warn(message);
            } else {
                log.info(message);
            }
            MDC.remove("traceId");
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String headerTrace = request.getHeader("X-Request-Id");
        if (headerTrace != null && !headerTrace.isBlank()) {
            return headerTrace.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String buildPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return uri;
        }
        return uri + "?" + query;
    }
}
