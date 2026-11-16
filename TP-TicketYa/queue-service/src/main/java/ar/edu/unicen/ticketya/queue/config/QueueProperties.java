package ar.edu.unicen.ticketya.queue.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        int batchSize,
        int intervaloSegundos,
        String internalToken,
        long schedulerMs,
        boolean schedulerEnabled,
        Token token) {

    public record Token(String secret, Duration ttl) {
    }
}
