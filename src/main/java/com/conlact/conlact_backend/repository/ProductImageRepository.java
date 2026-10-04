package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(UUID productId);

    Optional<ProductImage> findByIdAndProductId(UUID id, UUID productId);

    boolean existsByProductIdAndStoragePath(UUID productId, String storagePath);

    long countByProductId(UUID productId);

    void deleteByProductId(UUID productId);
}
