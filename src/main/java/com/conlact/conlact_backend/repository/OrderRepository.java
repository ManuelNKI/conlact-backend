package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.Order;
import com.conlact.conlact_backend.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByOrderNumber(Long orderNumber);
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
}
