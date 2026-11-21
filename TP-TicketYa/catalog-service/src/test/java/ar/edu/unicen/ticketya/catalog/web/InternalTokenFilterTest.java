package ar.edu.unicen.ticketya.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalTokenFilterTest {

    private final InternalTokenFilter filtro = new InternalTokenFilter(
            new InternalToken("secreto"), new ObjectMapper());

    @Test
    void sinTokenRechazaAntesDeLlegarAlControllerConProblemJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/internal/events/" + UUID.randomUUID() + "/report");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invocado = {false};

        filtro.doFilter(request, response, (req, res) -> invocado[0] = true);

        assertThat(invocado[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"UNAUTHORIZED\"")
                .contains("X-Internal-Token");
    }

    @Test
    void conTokenDejaPasarElRequestInterno() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/internal/events/" + UUID.randomUUID() + "/report");
        request.addHeader("X-Internal-Token", "secreto");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invocado = {false};

        filtro.doFilter(request, response, (req, res) -> invocado[0] = true);

        assertThat(invocado[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void unTokenInvalidoTampocoPasa() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/internal/recommendations/candidates");
        request.addHeader("X-Internal-Token", "otro");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invocado = {false};

        filtro.doFilter(request, response, (req, res) -> invocado[0] = true);

        assertThat(invocado[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void lasRutasPublicasYPorApiDocsNoPidenToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalog/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invocado = {false};

        filtro.doFilter(request, response, (req, res) -> invocado[0] = true);

        assertThat(invocado[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
