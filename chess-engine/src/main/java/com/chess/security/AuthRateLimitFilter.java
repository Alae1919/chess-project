package com.chess.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing and mass registration: each address may try to log in, and
 * to register, only so many times a minute. Not a substitute for a real gateway, but nothing
 * stood in the way before. The counts live in memory, so each instance counts on its own.
 *
 * Not a {@code @Component}: it is added to the security chain by hand, so the servlet
 * container doesn't register it a second time.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    /** Past this many tracked addresses, stale ones are dropped on the next request. */
    private static final int PRUNE_ABOVE = 10_000;

    private final int perMinute;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(int perMinute) {
        this.perMinute = perMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || bucketOf(request) == null;
    }

    /** "login" or "register" for the limited endpoints, else null. Each counts separately. */
    private static String bucketOf(HttpServletRequest request) {
        return switch (request.getRequestURI()) {
            case "/api/auth/login"    -> "login";
            case "/api/auth/register" -> "register";
            default                   -> null;
        };
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (attempts.size() > PRUNE_ABOVE) prune(now);

        String key = bucketOf(request) + ":" + clientAddress(request);
        long retryAfterMs = claim(key, now);
        if (retryAfterMs > 0) {
            long seconds = Math.max(1, (retryAfterMs + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(seconds));
            response.setContentType("application/problem+json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"type\":\"https://chess-engine/errors/too-many-requests\","
                + "\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Too many attempts. Try again in " + seconds + " seconds.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Counts one attempt for {@code key}. Returns 0 if it is allowed, else how many
     * milliseconds until the oldest attempt in the window drops out.
     */
    private long claim(String key, long now) {
        Deque<Long> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) times.pollFirst();
            if (times.size() >= perMinute) return WINDOW_MS - (now - times.peekFirst());
            times.addLast(now);
            return 0;
        }
    }

    private void prune(long now) {
        attempts.entrySet().removeIf(e -> {
            Deque<Long> times = e.getValue();
            synchronized (times) {
                return times.isEmpty() || now - times.peekLast() >= WINDOW_MS;
            }
        });
    }

    /**
     * The caller's address. Behind our own proxy (a private or loopback peer) it is the last
     * entry of X-Forwarded-For, the one the proxy appended itself; anyone else's header is
     * ignored, since a client could write whatever it liked there to dodge the limit.
     */
    static String clientAddress(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (isInternal(peer)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                return hops[hops.length - 1].trim();
            }
        }
        return peer;
    }

    private static boolean isInternal(String address) {
        try {
            InetAddress ip = InetAddress.getByName(address); // a literal: no lookup happens
            return ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
