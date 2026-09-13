package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.ShippingZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShippingZoneRepository extends JpaRepository<ShippingZone, UUID> {
    List<ShippingZone> findByIsActiveTrue();
}
