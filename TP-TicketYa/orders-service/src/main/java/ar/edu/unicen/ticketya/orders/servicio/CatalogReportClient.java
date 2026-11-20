package ar.edu.unicen.ticketya.orders.servicio;

import java.util.UUID;

/** Datos de capacidad y precios de catalog para el reporte (tarea 6.5). */
public interface CatalogReportClient {

    /**
     * Obtiene el reporte interno del evento.
     *
     * @throws CatalogEventNotFoundException si catalog responde 404
     * @throws org.springframework.web.client.RestClientException si catalog no responde
     */
    CatalogReport obtenerReporte(UUID eventoId);
}
