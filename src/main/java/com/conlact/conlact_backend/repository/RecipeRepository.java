package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
    Optional<Recipe> findBySlug(String slug);
    List<Recipe> findByIsPublishedTrue();
}
