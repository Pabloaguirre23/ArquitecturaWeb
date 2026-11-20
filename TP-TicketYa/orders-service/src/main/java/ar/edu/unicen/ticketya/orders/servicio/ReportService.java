package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Devolucion;
import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoDevolucion;
import ar.edu.unicen.ticketya.orders.dominio.EstadoOrden;
import ar.edu.unicen.ticketya.orders.dominio.EstadoReintegro;
import ar.edu.unicen.ticketya.orders.dominio.Orden;
import ar.edu.unicen.ticketya.orders.dominio.OrdenItem;
import ar.edu.unicen.ticketya.orders.dominio.Pago;
import ar.edu.unicen.ticketya.orders.dominio.Reintegro;
import ar.edu.unicen.ticketya.orders.repositorio.DevolucionRepository;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenItemRepository;
import ar.edu.unicen.ticketya.orders.repositorio.OrdenRepository;
import ar.edu.unicen.ticketya.orders.repositorio.PagoRepository;
import ar.edu.unicen.ticketya.orders.repositorio.ReintegroRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.RequestIdentity;
import ar.edu.unicen.ticketya.orders.web.dto.EventReportResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Orquesta el reporte de un evento (tarea 6.5). */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final Set<String> ROLES_PERMITIDOS = Set.of("ORGANIZER", "ADMIN");

    private final CatalogReportClient catalogReportClient;
    private final CatalogReportCache catalogReportCache;
    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final EntradaRepository entradaRepository;
    private final DevolucionRepository devolucionRepository;
    private final PagoRepository pagoRepository;
    private final ReintegroRepository reintegroRepository;
    private final ReportCalculator calculator;
    private final Clock clock;

    public ReportService(CatalogReportClient catalogReportClient, CatalogReportCache catalogReportCache,
                         OrdenRepository ordenRepository, OrdenItemRepository ordenItemRepository,
                         EntradaRepository entradaRepository, DevolucionRepository devolucionRepository,
                         PagoRepository pagoRepository, ReintegroRepository reintegroRepository,
                         ReportCalculator calculator, Clock clock) {
        this.catalogReportClient = catalogReportClient;
        this.catalogReportCache = catalogReportCache;
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.entradaRepository = entradaRepository;
        this.devolucionRepository = devolucionRepository;
        this.pagoRepository = pagoRepository;
        this.reintegroRepository = reintegroRepository;
        this.calculator = calculator;
        this.clock = clock;
    }

    public EventReportResponse reporte(UUID eventoId, RequestIdentity identity) {
        if (!ROLES_PERMITIDOS.contains(identity.role())) {
            throw ApiException.forbidden("FORBIDDEN",
                    "El rol " + identity.role() + " no puede consultar reportes");
        }

        RespuestaCatalogo catalogo = obtenerCatalogo(eventoId);
        List<Orden> ordenes = ordenRepository.findByEventoIdAndEstado(eventoId, EstadoOrden.PAGADA);
        List<UUID> ordenIds = ordenes.stream().map(Orden::getId).toList();
        List<OrdenItem> items = ordenIds.isEmpty()
                ? List.of()
                : ordenItemRepository.findByOrdenIdIn(ordenIds);
        List<Entrada> entradas = entradaRepository.findByEventoId(eventoId);

        Set<UUID> idsEntradas = entradas.stream().map(Entrada::getId).collect(Collectors.toSet());
        Set<UUID> entradasDevueltas = devolucionRepository.findByEstado(EstadoDevolucion.APROBADA)
                .stream()
                .map(Devolucion::getEntradaId)
                .filter(idsEntradas::contains)
                .collect(Collectors.toSet());

        List<Pago> pagos = ordenIds.isEmpty()
                ? List.of()
                : pagoRepository.findByOrdenIdIn(ordenIds);
        List<UUID> pagoIds = pagos.stream().map(Pago::getId).toList();
        List<Reintegro> reintegros = pagoIds.isEmpty()
                ? List.of()
                : reintegroRepository.findByPagoIdInAndEstado(pagoIds, EstadoReintegro.APROBADO);

        UUID organizadorId = catalogo.organizadorId() != null
                ? catalogo.organizadorId()
                : ordenes.stream()
                        .map(Orden::getEventoOrganizadorId)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
        if (organizadorId == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE",
                    "No se pudo verificar el organizador del evento");
        }
        if (!"ADMIN".equals(identity.role()) && !identity.userId().equals(organizadorId)) {
            throw ApiException.forbidden("NOT_EVENT_OWNER", "El evento pertenece a otro organizador");
        }

        return calculator.calcular(new ReportData(
                eventoId,
                organizadorId,
                ordenes,
                items,
                entradas,
                entradasDevueltas,
                reintegros,
                catalogo.sectores(),
                catalogo.desactualizado(),
                OffsetDateTime.now(clock)));
    }

    private RespuestaCatalogo obtenerCatalogo(UUID eventoId) {
        try {
            CatalogReport reporte = catalogReportClient.obtenerReporte(eventoId);
            if (reporte != null) {
                catalogReportCache.guardar(eventoId, reporte);
                return new RespuestaCatalogo(reporte.organizadorId(), reporte.sectores(), false);
            }
        } catch (CatalogEventNotFoundException ex) {
            throw ApiException.notFound("EVENT_NOT_FOUND", "El evento " + eventoId + " no existe");
        } catch (RuntimeException ex) {
            log.warn("Catalog no respondió para el reporte de {}: {}", eventoId, ex.getMessage());
        }
        CatalogReport enCache = catalogReportCache.obtener(eventoId);
        if (enCache != null) {
            return new RespuestaCatalogo(enCache.organizadorId(), enCache.sectores(), true);
        }
        // Sin catalog: el cálculo usa el snapshot de orders y marca datos desactualizados.
        return new RespuestaCatalogo(null, List.of(), true);
    }

    private record RespuestaCatalogo(UUID organizadorId, List<CatalogSector> sectores,
                                     boolean desactualizado) {
    }
}
