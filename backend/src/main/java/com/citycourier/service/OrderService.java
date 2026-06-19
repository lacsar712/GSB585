package com.citycourier.service;

import com.citycourier.dto.*;
import com.citycourier.entity.OrderEntity;
import com.citycourier.entity.OrderStatus;
import com.citycourier.entity.RoleType;
import com.citycourier.entity.User;
import com.citycourier.exception.BizException;
import com.citycourier.repository.OrderRepository;
import com.citycourier.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(OrderStatus.CREATED, Set.of(OrderStatus.ASSIGNED, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.ASSIGNED, Set.of(OrderStatus.PICKED_UP, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.PICKED_UP, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.DELIVERED, Set.of());
        TRANSITIONS.put(OrderStatus.CANCELLED, Set.of());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public OrderResponse create(CreateOrderRequest request) {
        validateOrderParties(request);

        User current = authService.getCurrentUserEntity();
        OrderEntity entity = new OrderEntity();
        entity.setTrackingNo(generateTrackingNo());
        entity.setSenderName(request.getSenderName());
        entity.setSenderPhone(request.getSenderPhone());
        entity.setSenderAddress(request.getSenderAddress());
        entity.setReceiverName(request.getReceiverName());
        entity.setReceiverPhone(request.getReceiverPhone());
        entity.setReceiverAddress(request.getReceiverAddress());
        entity.setItemName(request.getItemName());
        entity.setWeightKg(request.getWeightKg());
        entity.setDistanceKm(request.getDistanceKm());
        entity.setEstimatedFee(calcEstimatedFee(request.getDistanceKm(), request.getWeightKg()));
        entity.setStatus(OrderStatus.CREATED);
        entity.setCreatedBy(current);

        OrderEntity saved = orderRepository.save(entity);
        log.info("创建订单成功 trackingNo={} by={}", saved.getTrackingNo(), current.getUsername());
        return OrderResponse.from(saved);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public OrderResponse update(Long id, UpdateOrderRequest request) {
        OrderEntity entity = getOrder(id);
        User current = authService.getCurrentUserEntity();

        if (current.getRole() == RoleType.CUSTOMER && !entity.getCreatedBy().getId().equals(current.getId())) {
            throw new BizException("仅可编辑本人创建的订单");
        }
        if (entity.getStatus() != OrderStatus.CREATED) {
            throw new BizException("仅待接单状态可编辑");
        }

        validateOrderParties(request);

        entity.setSenderName(request.getSenderName().trim());
        entity.setSenderPhone(request.getSenderPhone().trim());
        entity.setSenderAddress(request.getSenderAddress().trim());
        entity.setReceiverName(request.getReceiverName().trim());
        entity.setReceiverPhone(request.getReceiverPhone().trim());
        entity.setReceiverAddress(request.getReceiverAddress().trim());
        entity.setItemName(request.getItemName().trim());
        entity.setWeightKg(request.getWeightKg());
        entity.setDistanceKm(request.getDistanceKm());
        entity.setEstimatedFee(calcEstimatedFee(request.getDistanceKm(), request.getWeightKg()));

        OrderEntity saved = orderRepository.save(entity);
        log.info("编辑订单成功 orderId={} by={}", id, current.getUsername());
        return OrderResponse.from(saved);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public void delete(Long id) {
        OrderEntity entity = getOrder(id);
        User current = authService.getCurrentUserEntity();

        if (current.getRole() == RoleType.CUSTOMER && !entity.getCreatedBy().getId().equals(current.getId())) {
            throw new BizException("仅可删除本人创建的订单");
        }
        if (entity.getStatus() != OrderStatus.CREATED) {
            throw new BizException("仅待接单状态可删除");
        }

        orderRepository.delete(entity);
        log.info("删除订单成功 orderId={} by={}", id, current.getUsername());
    }

    private void validateOrderParties(CreateOrderRequest request) {
        String senderName = normalizeText(request.getSenderName());
        String receiverName = normalizeText(request.getReceiverName());
        String senderPhone = normalizePhone(request.getSenderPhone());
        String receiverPhone = normalizePhone(request.getReceiverPhone());
        String senderAddress = normalizeText(request.getSenderAddress());
        String receiverAddress = normalizeText(request.getReceiverAddress());

        if (senderName.equals(receiverName)) {
            throw new BizException("发件人姓名与收件人姓名不能相同");
        }
        if (senderPhone.equals(receiverPhone)) {
            throw new BizException("发件人电话与收件人电话不能相同");
        }
        if (senderAddress.equals(receiverAddress)) {
            throw new BizException("发件地址与收件地址不能相同");
        }
    }

    private void validateOrderParties(UpdateOrderRequest request) {
        String senderName = normalizeText(request.getSenderName());
        String receiverName = normalizeText(request.getReceiverName());
        String senderPhone = normalizePhone(request.getSenderPhone());
        String receiverPhone = normalizePhone(request.getReceiverPhone());
        String senderAddress = normalizeText(request.getSenderAddress());
        String receiverAddress = normalizeText(request.getReceiverAddress());

        if (senderName.equals(receiverName)) {
            throw new BizException("发件人姓名与收件人姓名不能相同");
        }
        if (senderPhone.equals(receiverPhone)) {
            throw new BizException("发件人电话与收件人电话不能相同");
        }
        if (senderAddress.equals(receiverAddress)) {
            throw new BizException("发件地址与收件地址不能相同");
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private String normalizePhone(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER','RIDER','CUSTOMER')")
    public PageResponse<OrderResponse> list(OrderStatus status, String keyword, int page, int size) {
        User current = authService.getCurrentUserEntity();
        Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<OrderEntity> result = orderRepository.findAll(buildSpec(current, status, keyword), pageable);

        List<OrderResponse> rows = result.getContent().stream().map(OrderResponse::from).toList();
        return new PageResponse<>(rows, result.getNumber() + 1, result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER','RIDER','CUSTOMER')")
    public OrderResponse getByTrackingNo(String trackingNo) {
        User current = authService.getCurrentUserEntity();
        OrderEntity entity = orderRepository.findByTrackingNo(trackingNo)
                .orElseThrow(() -> new BizException("未找到对应运单"));

        if (current.getRole() == RoleType.CUSTOMER && !entity.getCreatedBy().getId().equals(current.getId())) {
            throw new BizException("无权查看该运单");
        }
        if (current.getRole() == RoleType.RIDER && (entity.getRider() == null || !entity.getRider().getId().equals(current.getId()))) {
            throw new BizException("无权查看该运单");
        }
        return OrderResponse.from(entity);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    public OrderResponse assign(Long id, AssignOrderRequest request) {
        OrderEntity entity = getOrder(id);
        if (entity.getStatus() != OrderStatus.CREATED) {
            throw new BizException("只有待接单状态才可派单");
        }

        User rider = userRepository.findByUsername(request.getRiderUsername())
                .filter(u -> u.getRole() == RoleType.RIDER)
                .orElseThrow(() -> new BizException("骑手不存在或角色错误"));

        entity.setRider(rider);
        entity.setStatus(OrderStatus.ASSIGNED);
        OrderEntity saved = orderRepository.save(entity);
        log.info("派单成功 orderId={} rider={}", id, rider.getUsername());
        return OrderResponse.from(saved);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER','RIDER')")
    public OrderResponse updateStatus(Long id, UpdateStatusRequest request) {
        OrderEntity entity = getOrder(id);
        User current = authService.getCurrentUserEntity();

        if (entity.getStatus() == OrderStatus.DELIVERED || entity.getStatus() == OrderStatus.CANCELLED) {
            throw new BizException("已完成或已取消订单禁止更新状态");
        }

        if (current.getRole() == RoleType.RIDER) {
            if (entity.getRider() == null || !entity.getRider().getId().equals(current.getId())) {
                throw new BizException("仅可更新本人订单");
            }
        }

        if (!TRANSITIONS.get(entity.getStatus()).contains(request.getTargetStatus())) {
            throw new BizException("状态流转不合法: " + entity.getStatus() + " -> " + request.getTargetStatus());
        }

        if (request.getTargetStatus() == OrderStatus.DELIVERED && request.getActualFee() == null) {
            throw new BizException("订单送达时必须填写实际费用");
        }

        entity.setStatus(request.getTargetStatus());
        if (request.getActualFee() != null) {
            entity.setActualFee(request.getActualFee());
        }
        OrderEntity saved = orderRepository.save(entity);
        log.info("状态更新成功 orderId={} status={}", id, saved.getStatus());
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    public OrderStatsResponse stats() {
        return new OrderStatsResponse(
                orderRepository.count(),
                orderRepository.countByStatus(OrderStatus.CREATED),
                orderRepository.countByStatus(OrderStatus.ASSIGNED),
                orderRepository.countByStatus(OrderStatus.PICKED_UP),
                orderRepository.countByStatus(OrderStatus.IN_TRANSIT),
                orderRepository.countByStatus(OrderStatus.DELIVERED),
                orderRepository.countByStatus(OrderStatus.CANCELLED)
        );
    }

    private OrderEntity getOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new BizException("订单不存在"));
    }

    private Specification<OrderEntity> buildSpec(User current, OrderStatus status, String keyword) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (current.getRole() == RoleType.CUSTOMER) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), current.getId()));
            } else if (current.getRole() == RoleType.RIDER) {
                predicates.add(cb.equal(root.get("rider").get("id"), current.getId()));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("trackingNo")), like),
                        cb.like(cb.lower(root.get("senderName")), like),
                        cb.like(cb.lower(root.get("senderPhone")), like),
                        cb.like(cb.lower(root.get("receiverName")), like),
                        cb.like(cb.lower(root.get("receiverPhone")), like),
                        cb.like(cb.lower(root.get("itemName")), like),
                        cb.like(cb.lower(root.get("senderAddress")), like),
                        cb.like(cb.lower(root.get("receiverAddress")), like)
                ));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private BigDecimal calcEstimatedFee(BigDecimal distanceKm, BigDecimal weightKg) {
        BigDecimal base = new BigDecimal("8.00");
        BigDecimal distanceFee = distanceKm.multiply(new BigDecimal("1.60"));
        BigDecimal weightFee = weightKg.multiply(new BigDecimal("0.80"));
        return base.add(distanceFee).add(weightFee).setScale(2, RoundingMode.HALF_UP);
    }

    private String generateTrackingNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "CC" + date + random;
    }
}
