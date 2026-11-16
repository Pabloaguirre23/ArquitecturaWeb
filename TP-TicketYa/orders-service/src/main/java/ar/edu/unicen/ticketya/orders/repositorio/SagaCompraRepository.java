package ar.edu.unicen.ticketya.orders.repositorio;

import ar.edu.unicen.ticketya.orders.dominio.EstadoSaga;
import ar.edu.unicen.ticketya.orders.dominio.SagaCompra;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaCompraRepository extends JpaRepository<SagaCompra, UUID> {

    List<SagaCompra> findByEstado(EstadoSaga estado);
}
