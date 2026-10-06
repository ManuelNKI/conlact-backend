package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    Optional<Recipe> findBySlug(String slug);

    List<Recipe> findByIsPublishedTrue();

    @Query(value = """
            SELECT
                p.id AS product_id,
                p.name AS product_name,
                MIN(pv.price) AS price,
                rp.is_recommended
            FROM recipe_products rp
            INNER JOIN products p
                ON p.id = rp.product_id
            LEFT JOIN product_variants pv
                ON pv.product_id = p.id
                AND pv.is_active = true
            WHERE rp.recipe_id = :recipeId
            GROUP BY p.id, p.name, rp.is_recommended
            """, nativeQuery = true)
    List<Object[]> findRelatedProductsByRecipeId(
            @Param("recipeId") UUID recipeId
    );
}