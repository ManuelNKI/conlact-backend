package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.AssociationImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssociationImageRepository extends JpaRepository<AssociationImage, UUID> {

    List<AssociationImage> findByAssociationIdOrderBySortOrderAscCreatedAtAscIdAsc(UUID associationId);

    Optional<AssociationImage> findByIdAndAssociationId(UUID id, UUID associationId);

    boolean existsByAssociationIdAndUrl(UUID associationId, String url);

    boolean existsByAssociationIdAndUrlAndIdNot(UUID associationId, String url, UUID id);
}
