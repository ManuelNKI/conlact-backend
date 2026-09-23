package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.Association;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssociationRepository extends JpaRepository<Association, UUID> {
    Optional<Association> findBySlug(String slug);

    List<Association> findByIsPublishedTrue();

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
