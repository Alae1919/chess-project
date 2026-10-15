package com.chess.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

/**
 * Gives every request an id and logs one line when it is done, with its status and duration.
 *
 * The id comes from nginx ({@code X-Request-Id}, the same one in its access log) or is made here,
 * goes into the logging context as {@code requestId} for every line logged while the request runs,
 * and back to the client in the response header, so one slow request can be followed from nginx's
 * log through the backend's.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** Its own logger, so the access lines can be turned off without touching the rest. */
    private static final Logger access = LoggerFactory.getLogger("http.access");
    /** Taken as given only if it looks like an id: it ends up in logs and in a response header. */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String given = req.getHeader(HEADER);
        String id = given != null && VALID_ID.matcher(given).matches() ? given : UUID.randomUUID().toString();
        res.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            // structured arguments: in the JSON log each is a field of its own
            access.info("{} {} {} {}",
                keyValue("method", req.getMethod()),
                keyValue("path", req.getRequestURI()),
                keyValue("status", res.getStatus()),
                keyValue("durationMs", ms));
            MDC.remove(MDC_KEY);
        }
    }

    /** The healthcheck and the metrics scrape come every few seconds and say nothing about players. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return req.getRequestURI().startsWith("/actuator/");
    }
}
