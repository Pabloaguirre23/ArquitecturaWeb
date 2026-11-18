package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoSaga;
import ar.edu.unicen.ticketya.orders.dominio.PasoSaga;
import ar.edu.unicen.ticketya.orders.dominio.SagaCompra;
import ar.edu.unicen.ticketya.orders.repositorio.SagaCompraRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste el avance de la saga de compra (tarea 6.2).
 *
 * <p>Los pasos intermedios se confirman en transacciones propias para que un
 * reinicio no pierda el progreso. Los pasos que deben ser atómicos con un cambio
 * de negocio (pago aprobado, orden pagada + entradas) usan las variantes
 * {@code ...EnTransaccion}, que se unen a la transacción del llamador.</p>
 */
@Service
public class SagaCompraService {

    private static final Logger log = LoggerFactory.getLogger(SagaCompraService.class);

    private final SagaCompraRepository repository;

    public SagaCompraService(SagaCompraRepository repository) {
        this.repository = repository;
    }

    /** Crea la saga junto con la orden; la reserva ya fue validada antes. */
    @Transactional
    public SagaCompra iniciar(UUID ordenId) {
        return repository.save(new SagaCompra(ordenId, PasoSaga.ORDEN_CREADA));
    }

    /** Avanza un paso y lo confirma de inmediato. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pasoCompletado(UUID ordenId, PasoSaga paso) {
        repository.findById(ordenId).ifPresentOrElse(saga -> {
            saga.avanzar(paso);
            repository.save(saga);
        }, () -> log.warn("No existe saga para la orden {}; no se registra el paso {}", ordenId, paso));
    }

    /** Avanza un paso dentro de la transacción del llamador. */
    @Transactional
    public void pasoCompletadoEnTransaccion(UUID ordenId, PasoSaga paso) {
        repository.findById(ordenId).ifPresentOrElse(saga -> {
            saga.avanzar(paso);
            repository.save(saga);
        }, () -> log.warn("No existe saga para la orden {}; no se registra el paso {}", ordenId, paso));
    }

    /** Marca la compra completa en la misma transacción que deja la orden PAGADA. */
    @Transactional
    public void completarEnTransaccion(UUID ordenId) {
        repository.findById(ordenId).ifPresentOrElse(saga -> {
            saga.avanzar(PasoSaga.ENTRADAS_EMITIDAS);
            saga.setEstado(EstadoSaga.COMPLETADA);
            repository.save(saga);
        }, () -> log.warn("No existe saga para la orden {}; no se marca COMPLETADA", ordenId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarError(UUID ordenId, String error) {
        repository.findById(ordenId).ifPresentOrElse(saga -> {
            saga.registrarError(error);
            repository.save(saga);
        }, () -> log.warn("No existe saga para la orden {}; no se registra el error", ordenId));
    }

    /**
     * Deja la saga en COMPENSANDO antes de ejecutar los comandos de compensación.
     *
     * @return {@code true} si la venta ya estaba confirmada en inventory, es decir,
     *         hay que devolver ítems en lugar de liberar la reserva.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean iniciarCompensacion(UUID ordenId, String motivo) {
        Optional<SagaCompra> encontrada = repository.findById(ordenId);
        if (encontrada.isEmpty()) {
            log.warn("No existe saga para la orden {}; la compensación se ejecuta sin estado", ordenId);
            return false;
        }
        SagaCompra saga = encontrada.get();
        boolean ventaConfirmada = saga.getPaso().ordinal() >= PasoSaga.INVENTARIO_CONFIRMADO.ordinal();
        saga.setEstado(EstadoSaga.COMPENSANDO);
        saga.registrarError(motivo);
        repository.save(saga);
        return ventaConfirmada;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarCompensada(UUID ordenId) {
        repository.findById(ordenId).ifPresent(saga -> {
            saga.setEstado(EstadoSaga.COMPENSADA);
            repository.save(saga);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarFallida(UUID ordenId, String motivo) {
        repository.findById(ordenId).ifPresent(saga -> {
            saga.setEstado(EstadoSaga.FALLIDA);
            saga.registrarError(motivo);
            repository.save(saga);
        });
    }

    @Transactional(readOnly = true)
    public Optional<SagaCompra> buscar(UUID ordenId) {
        return repository.findById(ordenId);
    }

    @Transactional(readOnly = true)
    public List<SagaCompra> compensando() {
        return repository.findByEstado(EstadoSaga.COMPENSANDO);
    }
}
