package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.Testimonial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestimonialRepository extends JpaRepository<Testimonial, UUID> {
    List<Testimonial> findByIsPublishedTrueAndIsAuthorizedTrue();

    @Query("""
            select t from Testimonial t
            where t.isPublished = true and t.isAuthorized = true
              and t.isApproved = true and t.isArchived = false
              and (:featuredOnly = false or t.isFeatured = true)
            order by t.createdAt desc, t.id asc
            """)
    List<Testimonial> findPublicTestimonials(@Param("featuredOnly") boolean featuredOnly);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Testimonial t where t.id = :id")
    Optional<Testimonial> findByIdForUpdate(UUID id);
}
