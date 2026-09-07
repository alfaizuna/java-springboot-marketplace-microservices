package com.alfaizunawebid.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alfaizunawebid.order.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
}
