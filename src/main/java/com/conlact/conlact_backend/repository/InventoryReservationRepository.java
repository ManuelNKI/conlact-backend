package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.InventoryReservation;
import com.conlact.conlact_backend.entity.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
    List<InventoryReservation> findByOrderId(UUID orderId);
    Optional<InventoryReservation> findByOrderIdAndProductVariantId(UUID orderId, UUID productVariantId);

    @Query(value = "SELECT * FROM public.inventory_reservations WHERE status = CAST(:status AS text)::reservation_status AND expires_at < :dateTime", nativeQuery = true)
    List<InventoryReservation> findByStatusAndExpiresAtBefore(@Param("status") String status, @Param("dateTime") OffsetDateTime dateTime);

    default List<InventoryReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, OffsetDateTime dateTime) {
        return findByStatusAndExpiresAtBefore(status.name(), dateTime);
    }
}
