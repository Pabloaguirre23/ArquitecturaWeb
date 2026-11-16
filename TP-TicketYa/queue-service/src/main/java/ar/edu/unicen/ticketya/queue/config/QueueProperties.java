package ar.edu.unicen.ticketya.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(int batchSize, int intervaloSegundos, String internalToken) {
}
