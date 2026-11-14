package ar.edu.unicen.ticketya.recommendation.repositorio;

import ar.edu.unicen.ticketya.recommendation.dominio.Preferencia;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PreferenciaRepository extends MongoRepository<Preferencia, String> {

    Optional<Preferencia> findByClienteId(String clienteId);
}
