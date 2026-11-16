package ar.edu.unicen.ticketya.queue.servicio;

import ar.edu.unicen.ticketya.queue.config.QueueProperties;
import ar.edu.unicen.ticketya.queue.web.ApiException;
import ar.edu.unicen.ticketya.queue.web.dto.FilaEstadoResponse;
import ar.edu.unicen.ticketya.queue.web.dto.PosicionResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Modelo Redis de la fila: sorted set de espera, hash de estado y hash de habilitados.
 * El ingreso usa ZADD NX. La tanda saca los primeros N en una sola ejecución de Lua.
 */
@Service
public class FilaService {

    static final String EN_ESPERA = "EN_ESPERA";
    static final String HABILITADO = "HABILITADO";
    static final String ABIERTA = "ABIERTA";
    static final String CERRADA = "CERRADA";
    static final String OPEN_KEY = "queue:open";

    private static final Logger log = LoggerFactory.getLogger(FilaService.class);

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> ingresarScript;
    private final DefaultRedisScript<List> abrirScript;
    private final DefaultRedisScript<List> habilitarScript;
    private final DefaultRedisScript<List> cerrarScript;
    private final DefaultRedisScript<Long> limpiarScript;
    private final QueueProperties properties;
    private final QueueTokenService tokens;
    private final Clock clock;

    public FilaService(StringRedisTemplate redis,
                       @Qualifier("ingresarScript") DefaultRedisScript<List> ingresarScript,
                       @Qualifier("abrirScript") DefaultRedisScript<List> abrirScript,
                       @Qualifier("habilitarScript") DefaultRedisScript<List> habilitarScript,
                       @Qualifier("cerrarScript") DefaultRedisScript<List> cerrarScript,
                       @Qualifier("limpiarScript") DefaultRedisScript<Long> limpiarScript,
                       QueueProperties properties,
                       QueueTokenService tokens,
                       Clock clock) {
        this.redis = redis;
        this.ingresarScript = ingresarScript;
        this.abrirScript = abrirScript;
        this.habilitarScript = habilitarScript;
        this.cerrarScript = cerrarScript;
        this.limpiarScript = limpiarScript;
        this.properties = properties;
        this.tokens = tokens;
        this.clock = clock;
    }

    public Apertura abrir(UUID eventoId, Integer batchSize, Integer intervaloSegundos) {
        int batch = batchSize == null ? properties.batchSize() : batchSize;
        int intervalo = intervaloSegundos == null ? properties.intervaloSegundos() : intervaloSegundos;
        List<?> resultado = redis.execute(
                abrirScript,
                List.of(estadoKey(eventoId), OPEN_KEY),
                Integer.toString(batch),
                Integer.toString(intervalo),
                Long.toString(clock.millis()),
                eventoId.toString());
        boolean creada = codigo(resultado, 0) == 1;
        return new Apertura(creada, new FilaEstadoResponse(
                eventoId,
                texto(resultado, 1),
                numero(resultado, 2),
                numero(resultado, 3),
                null));
    }

    public FilaEstadoResponse cerrar(UUID eventoId, String motivo) {
        List<?> resultado = redis.execute(
                cerrarScript,
                List.of(estadoKey(eventoId), OPEN_KEY, admitidosKey(eventoId)),
                motivo,
                Long.toString(clock.millis()),
                eventoId.toString());
        if (codigo(resultado, 0) == 0) {
            throw ApiException.notFound("QUEUE_NOT_OPEN", "Este evento no tiene fila virtual");
        }
        return new FilaEstadoResponse(
                eventoId,
                CERRADA,
                numero(resultado, 2),
                numero(resultado, 3),
                texto(resultado, 1));
    }

    public Ingreso ingresar(UUID eventoId, UUID userId) {
        List<?> resultado = redis.execute(
                ingresarScript,
                List.of(esperaKey(eventoId), estadoKey(eventoId), secuenciaKey(eventoId), admitidosKey(eventoId)),
                userId.toString(),
                Long.toString(clock.millis()));
        int codigo = codigo(resultado, 0);
        if (codigo == 0) {
            throw ApiException.conflict("QUEUE_NOT_OPEN", "Este evento no tiene fila virtual");
        }
        if (codigo == 1) {
            throw ApiException.conflict("QUEUE_CLOSED", "La fila de este evento no admite ingresos");
        }
        if (codigo == 4) {
            return new Ingreso(false, consultar(eventoId, userId));
        }
        int rank = numero(resultado, 1);
        int batch = numero(resultado, 2);
        int intervalo = numero(resultado, 3);
        return new Ingreso(codigo == 3, enEspera(eventoId, rank, batch, intervalo));
    }

    public PosicionResponse consultar(UUID eventoId, UUID userId) {
        if (CERRADA.equals(estadoFila(eventoId))) {
            return posicionCerrada(eventoId, userId);
        }
        PosicionResponse habilitado = habilitadoSiVigente(eventoId, userId);
        if (habilitado != null) {
            return habilitado;
        }
        Long rank = redis.opsForZSet().rank(esperaKey(eventoId), userId.toString());
        if (rank == null) {
            throw ApiException.notFound("NOT_IN_QUEUE", "El cliente no ingresó a la fila de este evento");
        }
        Object batch = redis.opsForHash().get(estadoKey(eventoId), "batchSize");
        Object intervalo = redis.opsForHash().get(estadoKey(eventoId), "intervaloSegundos");
        int batchSize = batch == null ? properties.batchSize() : Integer.parseInt(batch.toString());
        int intervaloSegundos = intervalo == null ? properties.intervaloSegundos() : Integer.parseInt(intervalo.toString());
        return enEspera(eventoId, rank.intValue(), batchSize, intervaloSegundos);
    }

    /**
     * Limpia tokens vencidos y habilita la próxima tanda de cada fila abierta
     * si ya pasó su intervalo. La extracción del sorted set es atómica.
     */
    public void procesarTandas(Instant ahora) {
        Set<String> abiertas = redis.opsForSet().members(OPEN_KEY);
        if (abiertas == null || abiertas.isEmpty()) {
            return;
        }
        for (String evento : abiertas) {
            UUID eventoId = UUID.fromString(evento);
            redis.execute(limpiarScript, List.of(admitidosKey(eventoId)), Long.toString(ahora.toEpochMilli()));
            habilitar(eventoId, ahora);
        }
    }

    private void habilitar(UUID eventoId, Instant ahora) {
        List<?> resultado = redis.execute(
                habilitarScript,
                List.of(esperaKey(eventoId), estadoKey(eventoId), admitidosKey(eventoId)),
                Long.toString(ahora.toEpochMilli()));
        if (resultado == null || resultado.isEmpty() || numero(resultado, 0) != 3) {
            return;
        }
        Instant expira = ahora.plus(properties.token().ttl());
        for (int i = 1; i < resultado.size(); i++) {
            UUID userId = UUID.fromString(resultado.get(i).toString());
            UUID jti = UUID.randomUUID();
            String jwt = tokens.emitir(eventoId, userId, jti, expira);
            redis.opsForHash().put(
                    admitidosKey(eventoId),
                    userId.toString(),
                    jti + "|" + expira.toEpochMilli() + "|" + jwt);
        }
        log.info("Tanda de {}: {} clientes habilitados", eventoId, resultado.size() - 1);
    }

    private String estadoFila(UUID eventoId) {
        Object estado = redis.opsForHash().get(estadoKey(eventoId), "estado");
        return estado == null ? null : estado.toString();
    }

    private PosicionResponse posicionCerrada(UUID eventoId, UUID userId) {
        Long rank = redis.opsForZSet().rank(esperaKey(eventoId), userId.toString());
        boolean estuvo = rank != null || redis.opsForHash().hasKey(admitidosKey(eventoId), userId.toString());
        if (!estuvo) {
            throw ApiException.notFound("NOT_IN_QUEUE", "El cliente no ingresó a la fila de este evento");
        }
        int posicion = rank == null ? 0 : rank.intValue() + 1;
        int adelante = rank == null ? 0 : rank.intValue();
        return new PosicionResponse(eventoId, posicion, adelante, CERRADA, 0, null);
    }

    private PosicionResponse habilitadoSiVigente(UUID eventoId, UUID userId) {
        Object valor = redis.opsForHash().get(admitidosKey(eventoId), userId.toString());
        if (valor == null || "PENDIENTE".equals(valor.toString()) || CERRADA.equals(valor.toString())) {
            return null;
        }
        String[] partes = valor.toString().split("\\|", 3);
        if (partes.length < 3) {
            return null;
        }
        long expira = Long.parseLong(partes[1]);
        if (expira <= clock.millis()) {
            redis.opsForHash().delete(admitidosKey(eventoId), userId.toString());
            return null;
        }
        return new PosicionResponse(eventoId, 0, 0, HABILITADO, 0, partes[2]);
    }

    private PosicionResponse enEspera(UUID eventoId, int rank, int batchSize, int intervaloSegundos) {
        int tandasPorDelante = rank / batchSize;
        int estimacion = (tandasPorDelante + 1) * intervaloSegundos;
        return new PosicionResponse(eventoId, rank + 1, rank, EN_ESPERA, estimacion, null);
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

    static String admitidosKey(UUID eventoId) {
        return "queue:" + eventoId + ":admitted";
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
