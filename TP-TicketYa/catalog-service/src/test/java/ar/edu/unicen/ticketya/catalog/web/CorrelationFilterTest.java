package ar.edu.unicen.ticketya.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationFilterTest {

    private final CorrelationFilter filtro = new CorrelationFilter();

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @Test
    void usaElHeaderRecibidoYLoDejaEnElMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalog/events");
        request.addHeader(CorrelationFilter.CABECERA, "corr-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duranteElRequest = new AtomicReference<>();

        filtro.doFilter(request, response, (req, res) ->
                duranteElRequest.set(MDC.get(CorrelationFilter.CLAVE_MDC)));

        assertThat(duranteElRequest.get()).isEqualTo("corr-42");
        assertThat(response.getHeader(CorrelationFilter.CABECERA)).isEqualTo("corr-42");
        assertThat(MDC.get(CorrelationFilter.CLAVE_MDC)).isNull();
    }

    @Test
    void sinHeaderGeneraUnaCorrelacionNueva() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalog/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duranteElRequest = new AtomicReference<>();

        filtro.doFilter(request, response, (req, res) ->
                duranteElRequest.set(MDC.get(CorrelationFilter.CLAVE_MDC)));

        assertThat(duranteElRequest.get()).isNotBlank();
        assertThat(response.getHeader(CorrelationFilter.CABECERA)).isEqualTo(duranteElRequest.get());
        assertThat(MDC.get(CorrelationFilter.CLAVE_MDC)).isNull();
    }
}
