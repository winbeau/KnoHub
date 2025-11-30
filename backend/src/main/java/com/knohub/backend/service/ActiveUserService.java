package com.knohub.backend.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks unique visitor IPs within a rolling window.
 */
@Service
public class ActiveUserService {

    private static final Duration RETENTION = Duration.ofHours(24);
    private final ConcurrentHashMap<String, Instant> visitors = new ConcurrentHashMap<>();

    public void recordIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        visitors.put(ip, now);
        cleanup(now);
    }

    public int getUniqueVisitorCount() {
        cleanup(Instant.now());
        return visitors.size();
    }

    private void cleanup(Instant now) {
        Instant cutoff = now.minus(RETENTION);
        visitors.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }
}
