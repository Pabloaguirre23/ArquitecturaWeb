package ar.edu.unicen.ticketya.queue.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScripts {

    @Bean(name = "ingresarScript")
    DefaultRedisScript<List> ingresarScript() {
        return script("redis/ingresar.lua");
    }

    @Bean(name = "abrirScript")
    DefaultRedisScript<List> abrirScript() {
        return script("redis/abrir.lua");
    }

    private static DefaultRedisScript<List> script(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }
}
