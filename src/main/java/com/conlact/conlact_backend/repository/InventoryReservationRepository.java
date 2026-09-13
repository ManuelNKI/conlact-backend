package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.InventoryReservation;
import com.conlact.conlact_backend.entity.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
    List<InventoryReservation> findByOrderId(UUID orderId);
    Optional<InventoryReservation> findByOrderIdAndProductVariantId(UUID orderId, UUID productVariantId);
    List<InventoryReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, OffsetDateTime dateTime);
}
