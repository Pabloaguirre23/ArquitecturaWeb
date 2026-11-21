package ar.edu.unicen.ticketya.recommendation.web.dto;

/**
 * Coincidencia de preferencias para notification-service (8.3). El email es
 * opcional: recommendation no lo administra y notification usa su sender mock
 * cuando viene null.
 */
public record PreferenciaClienteResponse(String clienteId, String clienteEmail) {
}
