package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoPago;
import ar.edu.unicen.ticketya.orders.dominio.EstadoReintegro;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.PasoSaga;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.dominio.SagaCompra;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.ReintegroRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.dto.CancelacionEventoResponse;
import ar.edu.unicen.ticketya.orders.web.dto.CrearOrdenRequest;
import ar.edu.unicen.ticketya.orders.web.dto.OrderResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OrderService {

    private static final List<String> TOKENS_VALIDOS = List.of("APROBADO", "RECHAZADO", "LENTO");
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final EntradaRepository entradaRepository;
    private final PagoRepository pagoRepository;
    private final ReintegroRepository reintegroRepository;
    private final InventoryClient inventoryClient;
    private final CatalogClient catalogClient;
    private final PaymentGateway paymentGateway;
    private final OrderPersistenceService orderPersistenceService;
    private final OrderEmissionService orderEmissionService;
    private final OrderCompensationService orderCompensationService;
    private final SagaCompraService sagaCompraService;
    private final IdempotencyService idempotencyService;
    private final Clock clock;
    private final AuditService auditService;

    public OrderService(OrdenRepository ordenRepository,
                        OrdenItemRepository ordenItemRepository,
                        EntradaRepository entradaRepository,
                        PagoRepository pagoRepository,
                        ReintegroRepository reintegroRepository,
                        InventoryClient inventoryClient,
                        CatalogClient catalogClient,
                        PaymentGateway paymentGateway,
                        OrderPersistenceService orderPersistenceService,
                        OrderEmissionService orderEmissionService,
                        OrderCompensationService orderCompensationService,
                        SagaCompraService sagaCompraService,
                        IdempotencyService idempotencyService,
                        Clock clock,
                        AuditService auditService) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.entradaRepository = entradaRepository;
        this.pagoRepository = pagoRepository;
        this.reintegroRepository = reintegroRepository;
        this.inventoryClient = inventoryClient;
        this.catalogClient = catalogClient;
        this.paymentGateway = paymentGateway;
        this.orderPersistenceService = orderPersistenceService;
        this.orderEmissionService = orderEmissionService;
        this.orderCompensationService = orderCompensationService;
        this.sagaCompraService = sagaCompraService;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
        this.auditService = auditService;
    }

    /**
     * Crea la orden, cobra y emite entradas siguiendo la saga de compra (tareas
     * 4.3 a 4.7 y 6.2).
     *
     * <p>La Idempotency-Key se reclama antes de tocar inventory. Si la compra
     * falla, el reclamo se libera para permitir un reintento limpio.</p>
     */
    /**
     * La compra no corre dentro de una transacción que abarque los pasos HTTP:
     * cada paso persiste en su propia transacción corta (REQUIRES_NEW). Con la
     * transacción externa, la prueba de carga de 500/1000 usuarios agotaba el
     * pool de conexiones mientras esperaba a inventory/catalog.
     */
    public OrderResponse crear(CrearOrdenRequest request, UUID clienteId, String idempotencyKey) {
        validarIdempotencyKey(idempotencyKey);
        validarToken(request.token());

        String hash = idempotencyService.hash(request.reservaId().toString(),
                request.medioPago().name(), request.token());
        try {
            idempotencyService.registrar(idempotencyKey, hash, OffsetDateTime.now(clock));
        } catch (DataIntegrityViolationException ex) {
            try {
                return idempotencyService.resolverExistente(idempotencyKey, hash);
            } catch (ApiException apiEx) {
                if ("IDEMPOTENCY_KEY_EXPIRED".equals(apiEx.getCode())) {
                    idempotencyService.registrar(idempotencyKey, hash, OffsetDateTime.now(clock));
                } else {
                    throw apiEx;
                }
            }
        }

        try {
            OrderResponse response = ejecutarCompra(request, clienteId);
            idempotencyService.completar(idempotencyKey, response);
            return response;
        } catch (RuntimeException ex) {
            try {
                idempotencyService.liberar(idempotencyKey);
            } catch (RuntimeException ignored) {
                // best-effort: no ocultar la excepción original
            }
            throw ex;
        }
    }

    /**
     * Saga: RESERVA_VALIDADA → ORDEN_CREADA → PAGO_APROBADO →
     * INVENTARIO_CONFIRMADO → ENTRADAS_EMITIDAS.
     *
     * <p>Cada comando externo usa una clave idempotente derivada de la orden y
     * el paso. Si un paso posterior al pago falla, se compensa en orden inverso
     * y la orden nunca queda PAGADA.</p>
     */
    private OrderResponse ejecutarCompra(CrearOrdenRequest request, UUID clienteId) {
        ReservationDto reserva = consultarReserva(request.reservaId());
        validarReserva(reserva, clienteId);
        reserva = enriquecerConCatalog(reserva);

        // Paso RESERVA_VALIDADA cumplido. La orden y la saga nacen en una transacción corta.
        OrderPersistenceService.OrdenCreada creada =
                orderPersistenceService.crearPendiente(reserva, clienteId, request);
        Orden orden = creada.orden();
        List<OrdenItem> items = creada.items();
        Pago pago = creada.pago();
        BigDecimal total = pago.getMonto();

        PaymentGateway.PaymentResult resultado;
        try {
            resultado = paymentGateway.cobrar(orden.getId(), total, request.medioPago(), request.token(),
                    ClavesIdempotencia.paso(orden.getId(), PasoSaga.PAGO_APROBADO));
        } catch (PaymentGatewayException ex) {
            orderPersistenceService.registrarPagoRechazado(orden.getId(), pago.getId(), EstadoOrden.ERROR_PAGO);
            compensar(orden, items, EstadoOrden.ERROR_PAGO, "ERROR_PAGO");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                    "La pasarela de pagos falló o no respondió a tiempo");
        }

        if (resultado.estado() == EstadoPago.RECHAZADO) {
            orderPersistenceService.registrarPagoRechazado(orden.getId(), pago.getId(), EstadoOrden.RECHAZADA);
            return compensar(orden, items, EstadoOrden.RECHAZADA, "PAGO_RECHAZADO");
        }

        // Paso PAGO_APROBADO: el cobro y el avance de la saga se confirman juntos.
        orderPersistenceService.registrarPagoAprobado(orden.getId(), pago.getId(), resultado.externalId());

        try {
            inventoryClient.confirmarReserva(reserva.id(),
                    ClavesIdempotencia.paso(orden.getId(), PasoSaga.INVENTARIO_CONFIRMADO));
        } catch (RuntimeException ex) {
            sagaCompraService.registrarError(orden.getId(), "Inventario: no se pudo confirmar la reserva");
            return compensar(orden, items, EstadoOrden.REINTEGRO_PENDIENTE, "ERROR_CONFIRMACION");
        }
        sagaCompraService.pasoCompletado(orden.getId(), PasoSaga.INVENTARIO_CONFIRMADO);

        try {
            // La orden pasa a PAGADA en la misma transacción que emite las entradas.
            List<Entrada> entradas = orderEmissionService.marcarPagadaYEmitir(orden, items);
            // Misma correlación que el evento del outbox; la bitácora es best-effort.
            auditService.compraAprobada(orden, clienteId);
            return toResponse(orden, items, entradas);
        } catch (RuntimeException ex) {
            sagaCompraService.registrarError(orden.getId(), "Emisión: no se pudieron emitir las entradas");
            return compensar(orden, items, EstadoOrden.REINTEGRO_PENDIENTE, "ERROR_EMISION");
        }
    }

    /**
     * Compensación (tareas 4.7 y 6.2): la orden nunca queda PAGADA. El servicio
     * de compensación crea el reintegro pendiente, libera o devuelve los lugares
     * y deja la saga en COMPENSADA o reintentable en COMPENSANDO.
     */
    private OrderResponse compensar(Orden orden, List<OrdenItem> items, EstadoOrden estado, String motivo) {
        orden.setEstado(estado);
        orderCompensationService.compensar(orden.getId(), estado, motivo);
        return toResponse(orden, items, List.of());
    }

    /**
     * Enriquece el snapshot del evento con datos de catalog (tarea 6.3). Es
     * best-effort: si catalog falla o no responde, se sigue con la reserva
     * original para que la compra no se caiga por un servicio auxiliar.
     */
    private ReservationDto enriquecerConCatalog(ReservationDto reserva) {
        if (reserva == null || reserva.eventoId() == null) {
            return reserva;
        }
        try {
            EventoSnapshot snapshot = catalogClient.obtenerEvento(reserva.eventoId());
            return reserva.conSnapshot(snapshot);
        } catch (RuntimeException ex) {
            log.warn("No se pudo enriquecer el snapshot del evento {} desde catalog: {}",
                    reserva.eventoId(), ex.getMessage());
            return reserva;
        }
    }

    private ReservationDto consultarReserva(UUID reservaId) {
        try {
            ReservationDto reserva = inventoryClient.obtenerReserva(reservaId);
            if (reserva == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "INVENTORY_UNAVAILABLE",
                        "inventory devolvió una respuesta vacía");
            }
            return reserva;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw ApiException.notFound("RESERVATION_NOT_FOUND", "No existe la reserva " + reservaId);
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVENTORY_UNAVAILABLE",
                    "No se pudo consultar la reserva en inventory");
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVENTORY_UNAVAILABLE",
                    "No se pudo consultar la reserva en inventory");
        }
    }

    private void validarReserva(ReservationDto reserva, UUID clienteId) {
        if (!reserva.clienteId().equals(clienteId)) {
            throw ApiException.forbidden("NOT_RESERVATION_OWNER", "La reserva pertenece a otro cliente");
        }
        if (!"VIGENTE".equals(reserva.estado())
                || !reserva.expiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw ApiException.gone("RESERVATION_EXPIRED", "La reserva está vencida y no se puede pagar");
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse obtener(UUID ordenId, UUID clienteId) {
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "No existe la orden " + ordenId));
        if (!orden.getClienteId().equals(clienteId)) {
            throw ApiException.forbidden("ORDER_NOT_OWNER", "La orden pertenece a otro cliente");
        }
        return toResponse(orden, ordenItemRepository.findByOrdenId(ordenId),
                entradaRepository.findByOrdenId(ordenId));
    }

    /**
     * Cancelación de evento pedida por catalog (tarea 4.9, necesaria para 2.3).
     * Idempotente: repetir devuelve la misma cantidad de entradas en reintegro
     * y no vuelve a pedir reintegros ya aprobados.
     */
    @Transactional
    public CancelacionEventoResponse cancelarEvento(UUID eventoId) {
        List<Entrada> validas = entradaRepository.findByEventoIdAndEstado(eventoId, EstadoEntrada.VALIDA);
        validas.forEach(entrada -> entrada.setEstado(EstadoEntrada.EN_REINTEGRO));
        entradaRepository.saveAll(validas);

        List<Entrada> enReintegro = entradaRepository.findByEventoIdAndEstado(eventoId, EstadoEntrada.EN_REINTEGRO);
        int reintegrosAprobados = 0;
        for (Entrada entrada : enReintegro) {
            Pago pago = pagoRepository.findByOrdenId(entrada.getOrdenId()).stream()
                    .filter(p -> p.getEstado() == EstadoPago.APROBADO
                            || p.getEstado() == EstadoPago.REINTEGRO_PENDIENTE
                            || p.getEstado() == EstadoPago.PARCIALMENTE_REINTEGRADO
                            || p.getEstado() == EstadoPago.REINTEGRADO)
                    .findFirst()
                    .orElse(null);
            if (pago == null) {
                continue;
            }
            OrdenItem item = ordenItemRepository.findById(entrada.getOrdenItemId()).orElse(null);
            if (item == null) {
                continue;
            }
            BigDecimal monto = item.getPrecioUnitario();
            Reintegro reintegro = reintegroRepository.findByPagoId(pago.getId()).stream()
                    .filter(r -> entrada.getId().equals(r.getEntradaId()))
                    .findFirst()
                    .orElseGet(() -> reintegroRepository.save(
                            new Reintegro(pago.getId(), entrada.getId(), monto, EstadoReintegro.PENDIENTE)));
            if (reintegro.getEstado() != EstadoReintegro.APROBADO) {
                try {
                    paymentGateway.reintegrar(pago.getId(), monto,
                            ClavesIdempotencia.reintegroEntrada(pago.getId(), entrada.getId()));
                    reintegro.setEstado(EstadoReintegro.APROBADO);
                    reintegroRepository.save(reintegro);
                } catch (RuntimeException ex) {
                    // Queda PENDIENTE para reintento.
                }
            }
            if (reintegro.getEstado() == EstadoReintegro.APROBADO) {
                reintegrosAprobados++;
            }
            actualizarEstadoPago(pago);
        }
        CancelacionEventoResponse respuesta =
                new CancelacionEventoResponse(eventoId, enReintegro.size(), reintegrosAprobados);
        auditService.cancelacionEvento(eventoId, respuesta.entradasAfectadas(),
                respuesta.reintegrosSolicitados());
        return respuesta;
    }

    private void actualizarEstadoPago(Pago pago) {
        List<Reintegro> reintegros = reintegroRepository.findByPagoId(pago.getId());
        BigDecimal totalAprobado = reintegros.stream()
                .filter(r -> r.getEstado() == EstadoReintegro.APROBADO)
                .map(Reintegro::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hayPendientes = reintegros.stream().anyMatch(r -> r.getEstado() == EstadoReintegro.PENDIENTE);
        if (totalAprobado.compareTo(pago.getMonto()) >= 0) {
            pago.setEstado(EstadoPago.REINTEGRADO);
        } else if (hayPendientes) {
            pago.setEstado(EstadoPago.REINTEGRO_PENDIENTE);
        } else if (totalAprobado.compareTo(BigDecimal.ZERO) > 0) {
            pago.setEstado(EstadoPago.PARCIALMENTE_REINTEGRADO);
        }
        pagoRepository.save(pago);
    }

    private OrderResponse toResponse(Orden orden, List<OrdenItem> items, List<Entrada> entradas) {
        SagaCompra saga = sagaCompraService.buscar(orden.getId()).orElse(null);
        return OrderResponse.from(orden, items, entradas, saga);
    }

    private void validarIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() < 8 || idempotencyKey.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "La solicitud tiene campos inválidos",
                    List.of(Map.of("field", "Idempotency-Key",
                            "message", "es obligatoria y debe tener entre 8 y 100 caracteres")));
        }
    }

    private void validarToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (!TOKENS_VALIDOS.contains(token.trim().toUpperCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "La solicitud tiene campos inválidos",
                    List.of(Map.of("field", "token",
                            "message", "debe ser APROBADO, RECHAZADO o LENTO")));
        }
    }
}
