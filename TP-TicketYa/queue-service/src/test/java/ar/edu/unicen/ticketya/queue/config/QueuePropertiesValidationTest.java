package ar.edu.unicen.ticketya.queue.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class QueuePropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(PropsConfig.class);

    @Test
    void aceptaLosDefaultsDeDesarrollo() {
        runner.withPropertyValues(validas()).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rechazaBatchIntervaloYTtlInvalidos() {
        runner.withPropertyValues(validas("queue.batch-size=0"))
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(validas("queue.intervalo-segundos=0"))
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(validas("queue.token.ttl=0s"))
                .run(context -> assertThat(context).hasFailed());
    }

    private static String[] validas(String... reemplazo) {
        String[] base = {
                "queue.batch-size=50",
                "queue.intervalo-segundos=30",
                "queue.internal-token=ticketya-internal-dev",
                "queue.scheduler-ms=5000",
                "queue.scheduler-enabled=false",
                "queue.retention=7d",
                "queue.token.secret=ticketya-queue-dev-secret-key-32b",
                "queue.token.ttl=5m"
        };
        if (reemplazo.length == 0) {
            return base;
        }
        String clave = reemplazo[0].substring(0, reemplazo[0].indexOf('='));
        String[] propiedades = new String[base.length];
        for (int i = 0; i < base.length; i++) {
            propiedades[i] = base[i].startsWith(clave + "=") ? reemplazo[0] : base[i];
        }
        return propiedades;
    }

    @Configuration
    @EnableConfigurationProperties(QueueProperties.class)
    static class PropsConfig {
    }
}
