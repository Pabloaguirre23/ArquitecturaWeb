package ar.edu.unicen.ticketya.inventory.config;

import ar.edu.unicen.ticketya.inventory.web.InternalToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InternalTokenConfig implements WebMvcConfigurer {

    private final InternalToken internalToken;

    public InternalTokenConfig(@Value("${inventory.internal-token}") String expected) {
        this.internalToken = new InternalToken(expected);
    }

    @Bean
    InternalToken internalToken() {
        return internalToken;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                internalToken.require(request.getHeader("X-Internal-Token"));
                return true;
            }
        }).addPathPatterns("/internal/**");
    }
}
