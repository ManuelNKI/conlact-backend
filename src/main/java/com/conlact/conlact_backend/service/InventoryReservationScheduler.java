
package com.conlact.conlact_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryReservationScheduler {

    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000)
    public void releaseExpiredReservations() {
        int released = orderService.releaseExpiredReservations();

        if (released > 0) {
            log.info(
                    "Proceso automático finalizado. Reservas liberadas: {}",
                    released
            );
        }
    }
}
