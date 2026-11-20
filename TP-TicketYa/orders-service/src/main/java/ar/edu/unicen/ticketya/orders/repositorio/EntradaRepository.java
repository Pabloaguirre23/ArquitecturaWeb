package ar.edu.unicen.ticketya.orders.repositorio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntradaRepository extends JpaRepository<Entrada, UUID> {

    List<Entrada> findByOrdenId(UUID ordenId);

    List<Entrada> findByEventoIdAndEstado(UUID eventoId, EstadoEntrada estado);

    long countByEventoIdAndEstado(UUID eventoId, EstadoEntrada estado);

    Page<Entrada> findByClienteIdOrderByCreatedAtDesc(UUID clienteId, Pageable pageable);

    Page<Entrada> findByClienteIdAndEventoIdOrderByCreatedAtDesc(
            UUID clienteId, UUID eventoId, Pageable pageable);

    Optional<Entrada> findByQrCode(UUID qrCode);

    /**
     * Consumo atómico de la entrada: solo la primera transacción que cumple todas
     * las condiciones marca la fila como utilizada. Devuelve la cantidad de filas
     * afectadas (1 o 0), sin find+save, para que dos escaneos concurrentes no
     * puedan marcar la misma entrada dos veces.
     */
    @Modifying
    @Query("""
            UPDATE Entrada e
               SET e.estado = ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada.UTILIZADA,
                   e.usedAt = :ahora,
                   e.usedBy = :usuario
             WHERE e.id = :id
               AND e.eventoId = :eventoId
               AND e.estado = ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada.VALIDA
               AND e.usedAt IS NULL
            """)
    int consumir(@Param("id") UUID id, @Param("eventoId") UUID eventoId,
                 @Param("ahora") OffsetDateTime ahora, @Param("usuario") UUID usuario);
}
