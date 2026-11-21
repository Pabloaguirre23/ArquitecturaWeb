package ar.edu.unicen.ticketya.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationFilterLogTest {

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @Test
    void registraUnaLineaConIdentidadRutaYEstado() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(CorrelationFilter.class);
        // prepareForDeferredProcessing congela el MDC en el momento del log
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent evento) {
                evento.prepareForDeferredProcessing();
                super.append(evento);
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/catalog/venues");
            request.addHeader(CorrelationFilter.CABECERA, "corr-7");
            request.addHeader("X-User-Id", "11111111-1111-4111-8111-111111111111");
            request.addHeader("X-User-Role", "ADMIN");
            MockHttpServletResponse response = new MockHttpServletResponse();

            new CorrelationFilter().doFilter(request, response, (req, res) ->
                    ((MockHttpServletResponse) res).setStatus(201));

            assertThat(appender.list).hasSize(1);
            Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
            assertThat(mdc).containsEntry("correlationId", "corr-7");
            assertThat(mdc).containsEntry("userId", "11111111-1111-4111-8111-111111111111");
            assertThat(mdc).containsEntry("role", "ADMIN");
            assertThat(mdc).containsEntry("method", "POST");
            assertThat(mdc).containsEntry("path", "/api/catalog/venues");
            assertThat(mdc).containsEntry("status", "201");
            assertThat(mdc).containsKey("durationMs");
            assertThat(MDC.get("correlationId")).isNull();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void noRegistraLosEndpointsDeActuator() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(CorrelationFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new CorrelationFilter().doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                    new MockHttpServletResponse(), (req, res) -> { });

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }
}