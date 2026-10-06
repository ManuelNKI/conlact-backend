package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecipeProductRepository extends JpaRepository<Product, UUID> {

    @Query(value = """
            SELECT p.*
            FROM products p
            INNER JOIN recipe_products rp
                ON rp.product_id = p.id
            WHERE rp.recipe_id = :recipeId
              AND rp.is_recommended = true
            """, nativeQuery = true)
    List<Product> findRecommendedProductsByRecipeId(
            @Param("recipeId") UUID recipeId
    );
}