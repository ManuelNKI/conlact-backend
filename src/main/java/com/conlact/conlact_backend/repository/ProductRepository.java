package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    @Override
    @EntityGraph(attributePaths = {"association", "category"})
    Optional<Product> findById(UUID id);

    @EntityGraph(attributePaths = {"association", "category"})
    List<Product> findAllByOrderByNameAscIdAsc();

    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);

    Optional<Product> findBySlug(String slug);
    List<Product> findByStatus(ProductStatus status);
    List<Product> findByAssociationId(UUID associationId);
    List<Product> findByCategoryId(UUID categoryId);
    List<Product> findByIsFeaturedTrueAndStatus(ProductStatus status);
}
