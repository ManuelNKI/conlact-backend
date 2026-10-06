package com.conlact.conlact_backend.repository.specification;

import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.ProductVariant;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> isPublished() {
        return (root, query, cb) -> cb.equal(cb.function("text", String.class, root.get("status")), ProductStatus.published.name());
    }

    public static Specification<Product> byAssociation(String associationIdentifier) {
        return (root, query, cb) -> {
            if (associationIdentifier == null || associationIdentifier.isBlank()) {
                return null;
            }
            try {
                UUID assocId = UUID.fromString(associationIdentifier.trim());
                return cb.equal(root.get("association").get("id"), assocId);
            } catch (IllegalArgumentException e) {
                return cb.equal(cb.lower(root.get("association").get("slug")), associationIdentifier.trim().toLowerCase());
            }
        };
    }

    public static Specification<Product> byCategory(String categoryIdentifier) {
        return (root, query, cb) -> {
            if (categoryIdentifier == null || categoryIdentifier.isBlank()) {
                return null;
            }
            try {
                UUID catId = UUID.fromString(categoryIdentifier.trim());
                return cb.equal(root.get("category").get("id"), catId);
            } catch (IllegalArgumentException e) {
                return cb.equal(cb.lower(root.get("category").get("slug")), categoryIdentifier.trim().toLowerCase());
            }
        };
    }

    public static Specification<Product> isFeatured(Boolean featured) {
        return (root, query, cb) -> {
            if (featured == null) {
                return null;
            }
            return cb.equal(root.get("isFeatured"), featured);
        };
    }

    public static Specification<Product> searchByText(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return null;
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("cheeseType")), pattern)
            );
        };
    }

    public static Specification<Product> hasAvailableStock() {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            Join<Product, ProductVariant> variants = root.join("variants", JoinType.INNER);
            return cb.and(
                    cb.isTrue(variants.get("isActive")),
                    cb.gt(variants.get("stock"), 0)
            );
        };
    }
}
