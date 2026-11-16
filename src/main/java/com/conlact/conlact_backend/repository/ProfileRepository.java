package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.Profile;
import com.conlact.conlact_backend.entity.enums.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<Profile> findAllByOrderByCreatedAtDesc();

    long countByRoleAndIsActiveTrue(UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Profile p WHERE p.id = :id")
    Optional<Profile> findByIdForUpdate(@Param("id") UUID id);
}
