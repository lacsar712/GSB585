package com.citycourier.repository;

import com.citycourier.entity.OrderEntity;
import com.citycourier.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {
    Optional<OrderEntity> findByTrackingNo(String trackingNo);

    Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);

    long countByStatus(OrderStatus status);
}
