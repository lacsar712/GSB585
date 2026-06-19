package com.citycourier.controller;

import com.citycourier.dto.*;
import com.citycourier.entity.OrderStatus;
import com.citycourier.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok("获取订单列表成功", orderService.list(status, keyword, page, size));
    }

    @GetMapping("/tracking/{trackingNo}")
    public ApiResponse<OrderResponse> getByTracking(@PathVariable String trackingNo) {
        return ApiResponse.ok("查询运单成功", orderService.getByTrackingNo(trackingNo));
    }

    @PostMapping
    public ApiResponse<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok("创建订单成功", orderService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OrderResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateOrderRequest request) {
        return ApiResponse.ok("编辑订单成功", orderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ApiResponse.ok("删除订单成功", null);
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<OrderResponse> assign(@PathVariable Long id, @Valid @RequestBody AssignOrderRequest request) {
        return ApiResponse.ok("派单成功", orderService.assign(id, request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ApiResponse.ok("更新状态成功", orderService.updateStatus(id, request));
    }

    @GetMapping("/stats")
    public ApiResponse<OrderStatsResponse> stats() {
        return ApiResponse.ok("获取统计成功", orderService.stats());
    }
}
