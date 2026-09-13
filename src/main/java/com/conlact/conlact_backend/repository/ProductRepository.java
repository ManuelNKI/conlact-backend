package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySlug(String slug);
    List<Product> findByStatus(ProductStatus status);
    List<Product> findByAssociationId(UUID associationId);
    List<Product> findByCategoryId(UUID categoryId);
    List<Product> findByIsFeaturedTrueAndStatus(ProductStatus status);
}
