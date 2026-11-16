package ar.edu.unicen.ticketya.queue.servicio;

import ar.edu.unicen.ticketya.queue.config.QueueProperties;
import ar.edu.unicen.ticketya.queue.web.ApiException;
import ar.edu.unicen.ticketya.queue.web.dto.FilaEstadoResponse;
import ar.edu.unicen.ticketya.queue.web.dto.PosicionResponse;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Modelo Redis de la fila: sorted set de espera y hash de estado.
 * El ingreso usa ZADD NX para no pisar la posición de quien ya está.
 */
@Service
public class FilaService {

    static final String EN_ESPERA = "EN_ESPERA";
    static final String ABIERTA = "ABIERTA";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> ingresarScript;
    private final DefaultRedisScript<List> abrirScript;
    private final QueueProperties properties;
    private final Clock clock;

    public FilaService(StringRedisTemplate redis,
                       @Qualifier("ingresarScript") DefaultRedisScript<List> ingresarScript,
                       @Qualifier("abrirScript") DefaultRedisScript<List> abrirScript,
                       QueueProperties properties,
                       Clock clock) {
        this.redis = redis;
        this.ingresarScript = ingresarScript;
        this.abrirScript = abrirScript;
        this.properties = properties;
        this.clock = clock;
    }

    public Apertura abrir(UUID eventoId, Integer batchSize, Integer intervaloSegundos) {
        int batch = batchSize == null ? properties.batchSize() : batchSize;
        int intervalo = intervaloSegundos == null ? properties.intervaloSegundos() : intervaloSegundos;
        List<?> resultado = redis.execute(
                abrirScript,
                List.of(estadoKey(eventoId)),
                Integer.toString(batch),
                Integer.toString(intervalo));
        boolean creada = codigo(resultado, 0) == 1;
        return new Apertura(creada, new FilaEstadoResponse(
                eventoId,
                texto(resultado, 1),
                numero(resultado, 2),
                numero(resultado, 3)));
    }

    public Ingreso ingresar(UUID eventoId, UUID userId) {
        List<?> resultado = redis.execute(
                ingresarScript,
                List.of(esperaKey(eventoId), estadoKey(eventoId), secuenciaKey(eventoId)),
                userId.toString(),
                Long.toString(clock.millis()));
        int codigo = codigo(resultado, 0);
        if (codigo == 0) {
            throw ApiException.conflict("QUEUE_NOT_OPEN", "Este evento no tiene fila virtual");
        }
        if (codigo == 1) {
            throw ApiException.conflict("QUEUE_CLOSED", "La fila de este evento no admite ingresos");
        }
        int rank = numero(resultado, 1);
        int batch = numero(resultado, 2);
        int intervalo = numero(resultado, 3);
        return new Ingreso(codigo == 3, posicion(eventoId, rank, batch, intervalo));
    }

    public PosicionResponse consultar(UUID eventoId, UUID userId) {
        Long rank = redis.opsForZSet().rank(esperaKey(eventoId), userId.toString());
        if (rank == null) {
            throw ApiException.notFound("NOT_IN_QUEUE", "El cliente no ingresó a la fila de este evento");
        }
        Object batch = redis.opsForHash().get(estadoKey(eventoId), "batchSize");
        Object intervalo = redis.opsForHash().get(estadoKey(eventoId), "intervaloSegundos");
        int batchSize = batch == null ? properties.batchSize() : Integer.parseInt(batch.toString());
        int intervaloSegundos = intervalo == null ? properties.intervaloSegundos() : Integer.parseInt(intervalo.toString());
        return posicion(eventoId, rank.intValue(), batchSize, intervaloSegundos);
    }

    private PosicionResponse posicion(UUID eventoId, int rank, int batchSize, int intervaloSegundos) {
        int tandasPorDelante = rank / batchSize;
        int estimacion = (tandasPorDelante + 1) * intervaloSegundos;
        return new PosicionResponse(eventoId, rank + 1, rank, EN_ESPERA, estimacion);
    }

    static String esperaKey(UUID eventoId) {
        return "queue:" + eventoId + ":waiting";
    }

    static String estadoKey(UUID eventoId) {
        return "queue:" + eventoId + ":state";
    }

    static String secuenciaKey(UUID eventoId) {
        return "queue:" + eventoId + ":seq";
    }

    private static int codigo(List<?> resultado, int indice) {
        if (resultado == null || resultado.size() <= indice || resultado.get(indice) == null) {
            throw ApiException.conflict("QUEUE_UNAVAILABLE", "Redis no devolvió el estado de la fila");
        }
        return numero(resultado, indice);
    }

    private static int numero(List<?> resultado, int indice) {
        Object valor = resultado.get(indice);
        if (valor instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(valor.toString());
    }

    private static String texto(List<?> resultado, int indice) {
        Object valor = resultado.get(indice);
        return valor == null ? ABIERTA : valor.toString();
    }

    public record Apertura(boolean creada, FilaEstadoResponse estado) {
    }

    public record Ingreso(boolean nuevo, PosicionResponse posicion) {
    }
}
