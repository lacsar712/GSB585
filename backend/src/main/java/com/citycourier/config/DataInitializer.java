package com.citycourier.config;

import com.citycourier.entity.*;
import com.citycourier.repository.OrderRepository;
import com.citycourier.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        User admin = createUser("admin", "password123", RoleType.ADMIN);
        User dispatcher = createUser("dispatch", "password123", RoleType.DISPATCHER);
        User rider = createUser("rider1", "password123", RoleType.RIDER);
        User customer = createUser("customer1", "password123", RoleType.CUSTOMER);

        createOrder("CC202602070001", customer, null, OrderStatus.CREATED, "文件", new BigDecimal("2.0"), new BigDecimal("5.0"));
        createOrder("CC202602070002", customer, rider, OrderStatus.ASSIGNED, "蛋糕", new BigDecimal("1.2"), new BigDecimal("3.0"));
        createOrder("CC202602070003", customer, rider, OrderStatus.IN_TRANSIT, "药品", new BigDecimal("0.8"), new BigDecimal("7.0"));
        createOrder("CC202602070004", customer, rider, OrderStatus.DELIVERED, "鲜花", new BigDecimal("1.0"), new BigDecimal("4.5"));
    }

    private User createUser(String username, String rawPassword, RoleType role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private void createOrder(String trackingNo, User createdBy, User rider, OrderStatus status,
                             String itemName, BigDecimal weight, BigDecimal distance) {
        OrderEntity order = new OrderEntity();
        order.setTrackingNo(trackingNo);
        order.setSenderName("张三");
        order.setSenderPhone("13800000000");
        order.setSenderAddress("上海市浦东新区世纪大道1号");
        order.setReceiverName("李四");
        order.setReceiverPhone("13900000000");
        order.setReceiverAddress("上海市徐汇区漕溪北路88号");
        order.setItemName(itemName);
        order.setWeightKg(weight);
        order.setDistanceKm(distance);
        order.setEstimatedFee(new BigDecimal("15.80"));
        order.setActualFee(status == OrderStatus.DELIVERED ? new BigDecimal("16.00") : null);
        order.setStatus(status);
        order.setCreatedBy(createdBy);
        order.setRider(rider);
        orderRepository.save(order);
    }
}
