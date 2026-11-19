package ar.edu.unicen.ticketya.queue.servicio;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "queue.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class TandaScheduler {

    private final FilaService filaService;
    private final Clock clock;

    public TandaScheduler(FilaService filaService, Clock clock) {
        this.filaService = filaService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${queue.scheduler-ms:5000}")
    public void tick() {
        filaService.procesarTandas(clock.instant());
    }
}
