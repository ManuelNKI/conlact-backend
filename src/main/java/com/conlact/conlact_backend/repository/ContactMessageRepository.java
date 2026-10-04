package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
    List<ContactMessage> findByIsResolvedFalseOrderByCreatedAtDesc();

    List<ContactMessage> findAllByOrderByCreatedAtDesc();
}
