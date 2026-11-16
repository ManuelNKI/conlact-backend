package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.TrainingContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrainingContentRepository extends JpaRepository<TrainingContent, UUID> {
    List<TrainingContent> findBySectionIdOrderBySortOrderAscCreatedAtAsc(UUID sectionId);
    List<TrainingContent> findBySectionIdAndIsPublishedTrueOrderBySortOrderAscCreatedAtAsc(UUID sectionId);
}
