package ar.edu.unicen.ticketya.orders.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología mínima del productor: orders declara el exchange `ticketya.events`
 * y publica con la clave de enrutamiento igual al `type` del evento. Las colas
 * y sus DLQ las declaran los consumidores.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange ticketyaEventsExchange(
            @Value("${orders.outbox.exchange:ticketya.events}") String nombre) {
        return new TopicExchange(nombre, true, false);
    }
}
