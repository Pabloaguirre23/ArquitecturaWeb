package ar.edu.unicen.ticketya.recommendation.dominio;

/**
 * Tipos de evento aceptados en las preferencias. Deben coincidir con los del
 * catálogo para que las recomendaciones crucen datos sin traducciones.
 */
public enum TipoEvento {
    RECITAL,
    TEATRO,
    DEPORTE,
    CONFERENCIA,
    FESTIVAL,
    OTRO
}
