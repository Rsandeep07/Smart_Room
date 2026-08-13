package com.smartroom.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroom.backend.web.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared-secret check on the two ingest endpoints (Section 20.6).
 *
 * <p>Section 11's APIs are unauthenticated, which means anyone on the campus Wi-Fi can
 * post a person count of 400 and drive the recommendation. A shared key in a header is
 * the stated minimum. It is not transport security - the ESP32 speaks plain HTTP - so it
 * stops casual tampering on a trusted LAN and nothing more. That limit is worth being
 * honest about in the project report.
 *
 * <p>Read endpoints are deliberately open: the dashboard is a browser page on the same
 * LAN, and putting the ingest key into a React bundle would publish it.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private final String headerName;
    private final byte[] expectedKey;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(String headerName, String apiKey, ObjectMapper objectMapper) {
        this.headerName = headerName;
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // A CORS preflight carries no custom headers by definition, so checking the key
        // on OPTIONS would reject the browser's probe before the real request was ever sent.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String presented = request.getHeader(headerName);
        if (presented == null) {
            // Accept the standard bearer form too: some HTTP clients make a custom header
            // awkward, and it costs nothing to read both.
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                presented = authorization.substring(7).trim();
            }
        }

        if (!matches(presented)) {
            log.warn("Rejected unauthenticated ingest to {} from {}",
                    request.getRequestURI(), request.getRemoteAddr());
            writeUnauthorized(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    /** Constant-time comparison: a length-or-prefix shortcut here would leak the key one byte at a time. */
    private boolean matches(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedKey);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Missing or invalid %s header".formatted(headerName),
                request.getRequestURI()));
    }
}
