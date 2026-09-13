package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.TouristAttraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TouristAttractionRepository extends JpaRepository<TouristAttraction, UUID> {
    List<TouristAttraction> findByIsPublishedTrue();
    List<TouristAttraction> findByAssociationIdAndIsPublishedTrue(UUID associationId);
}
