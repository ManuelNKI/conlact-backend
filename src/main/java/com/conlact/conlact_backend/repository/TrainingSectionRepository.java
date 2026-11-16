package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.TrainingSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrainingSectionRepository extends JpaRepository<TrainingSection, UUID> {
    List<TrainingSection> findAllByOrderBySortOrderAscCreatedAtAsc();

    @Query("SELECT s FROM TrainingSection s WHERE s.isPublished = true ORDER BY s.sortOrder ASC, s.createdAt ASC")
    List<TrainingSection> findPublishedSections();
}
