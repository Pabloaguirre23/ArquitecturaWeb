package ar.edu.unicen.ticketya.queue.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        @Min(1) int batchSize,
        @Min(1) int intervaloSegundos,
        @NotBlank String internalToken,
        @Min(1) long schedulerMs,
        boolean schedulerEnabled,
        @NotNull Duration retention,
        @Valid @NotNull Token token) {

    @AssertTrue(message = "retention tiene que ser de al menos 1 minuto")
    public boolean isRetentionPositiva() {
        return retention != null && retention.compareTo(Duration.ofMinutes(1)) >= 0;
    }

    public record Token(@NotBlank String secret, @NotNull Duration ttl) {

        @AssertTrue(message = "ttl tiene que ser de al menos 1 segundo")
        public boolean isTtlPositivo() {
            return ttl != null && ttl.compareTo(Duration.ofSeconds(1)) >= 0;
        }
    }
}
