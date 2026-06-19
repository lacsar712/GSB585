package com.citycourier.dto;

import com.citycourier.entity.OrderEntity;
import com.citycourier.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OrderResponse {
    private Long id;
    private String trackingNo;
    private String senderName;
    private String senderPhone;
    private String senderAddress;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String itemName;
    private BigDecimal weightKg;
    private BigDecimal distanceKm;
    private BigDecimal estimatedFee;
    private BigDecimal actualFee;
    private OrderStatus status;
    private String createdBy;
    private String rider;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderResponse from(OrderEntity entity) {
        return OrderResponse.builder()
                .id(entity.getId())
                .trackingNo(entity.getTrackingNo())
                .senderName(entity.getSenderName())
                .senderPhone(entity.getSenderPhone())
                .senderAddress(entity.getSenderAddress())
                .receiverName(entity.getReceiverName())
                .receiverPhone(entity.getReceiverPhone())
                .receiverAddress(entity.getReceiverAddress())
                .itemName(entity.getItemName())
                .weightKg(entity.getWeightKg())
                .distanceKm(entity.getDistanceKm())
                .estimatedFee(entity.getEstimatedFee())
                .actualFee(entity.getActualFee())
                .status(entity.getStatus())
                .createdBy(entity.getCreatedBy().getUsername())
                .rider(entity.getRider() == null ? null : entity.getRider().getUsername())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
