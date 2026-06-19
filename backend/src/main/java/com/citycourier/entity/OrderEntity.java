package com.citycourier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String trackingNo;

    @Column(nullable = false, length = 60)
    private String senderName;

    @Column(nullable = false, length = 30)
    private String senderPhone;

    @Column(nullable = false, length = 200)
    private String senderAddress;

    @Column(nullable = false, length = 60)
    private String receiverName;

    @Column(nullable = false, length = 30)
    private String receiverPhone;

    @Column(nullable = false, length = 200)
    private String receiverAddress;

    @Column(nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal weightKg;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedFee;

    @Column(precision = 10, scale = 2)
    private BigDecimal actualFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id")
    private User rider;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
