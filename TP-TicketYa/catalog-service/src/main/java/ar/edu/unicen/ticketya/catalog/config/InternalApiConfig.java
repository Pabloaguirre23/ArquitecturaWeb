package ar.edu.unicen.ticketya.catalog.config;

import ar.edu.unicen.ticketya.catalog.web.InternalToken;
import ar.edu.unicen.ticketya.catalog.web.InternalTokenFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra el filtro de token interno para /internal/**. Se hace con
 * FilterRegistrationBean (y no con @Component) para que los slices
 * @WebMvcTest sigan armándose sin el bean InternalToken.
 */
@Configuration
public class InternalApiConfig {

    @Bean
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilter(
            InternalToken internalToken, ObjectMapper objectMapper) {
        FilterRegistrationBean<InternalTokenFilter> registro =
                new FilterRegistrationBean<>(new InternalTokenFilter(internalToken, objectMapper));
        registro.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registro;
    }
}
