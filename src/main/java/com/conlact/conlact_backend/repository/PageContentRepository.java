package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.PageContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PageContentRepository extends JpaRepository<PageContent, String> {
    Optional<PageContent> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
