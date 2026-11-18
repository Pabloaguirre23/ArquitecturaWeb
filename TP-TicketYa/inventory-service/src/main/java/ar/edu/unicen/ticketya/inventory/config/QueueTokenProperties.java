package ar.edu.unicen.ticketya.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue.token")
public record QueueTokenProperties(String secret) {
}
