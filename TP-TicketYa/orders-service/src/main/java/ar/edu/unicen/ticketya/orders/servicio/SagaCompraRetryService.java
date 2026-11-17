package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.SagaCompra;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Reintenta las compensaciones que quedaron en COMPENSANDO porque la pasarela o
 * inventory no respondieron (tarea 6.2). Es el proceso programado que vuelve
 * reintentable una compensación fallida.
 */
@Service
public class SagaCompraRetryService {

    private static final Logger log = LoggerFactory.getLogger(SagaCompraRetryService.class);

    private final SagaCompraService sagaCompraService;
    private final OrderCompensationService orderCompensationService;
    private final int maxIntentos;

    public SagaCompraRetryService(SagaCompraService sagaCompraService,
                                  OrderCompensationService orderCompensationService,
                                  @Value("${orders.sagas.max-intentos:10}") int maxIntentos) {
        this.sagaCompraService = sagaCompraService;
        this.orderCompensationService = orderCompensationService;
        this.maxIntentos = maxIntentos;
    }

    @Scheduled(fixedDelayString = "${orders.sagas.retry-delay-ms:60000}")
    public void reintentarCompensaciones() {
        for (SagaCompra saga : sagaCompraService.compensando()) {
            if (saga.getIntentos() >= maxIntentos) {
                sagaCompraService.marcarFallida(saga.getOrdenId(),
                        "La compensación agotó los " + maxIntentos + " reintentos");
                continue;
            }
            try {
                orderCompensationService.compensar(saga.getOrdenId(), null,
                        saga.getUltimoError() == null ? "REINTENTO_COMPENSACION" : saga.getUltimoError());
            } catch (RuntimeException ex) {
                log.warn("Falló el reintento de compensación de la orden {}: {}",
                        saga.getOrdenId(), ex.getMessage());
            }
        }
    }
}
