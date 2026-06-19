package com.citycourier.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateOrderRequest {

    @NotBlank(message = "发件人姓名不能为空")
    @Size(max = 32, message = "发件人姓名长度不能超过32")
    private String senderName;

    @NotBlank(message = "发件人电话不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "发件人电话格式不正确")
    private String senderPhone;

    @NotBlank(message = "发件地址不能为空")
    @Size(max = 255, message = "发件地址长度不能超过255")
    private String senderAddress;

    @NotBlank(message = "收件人姓名不能为空")
    @Size(max = 32, message = "收件人姓名长度不能超过32")
    private String receiverName;

    @NotBlank(message = "收件人电话不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "收件人电话格式不正确")
    private String receiverPhone;

    @NotBlank(message = "收件地址不能为空")
    @Size(max = 255, message = "收件地址长度不能超过255")
    private String receiverAddress;

    @NotBlank(message = "物品名称不能为空")
    @Size(max = 64, message = "物品名称长度不能超过64")
    private String itemName;

    @NotNull(message = "重量不能为空")
    @DecimalMin(value = "0.1", message = "重量最小为0.1kg")
    @DecimalMax(value = "50.0", message = "重量最大为50kg")
    private BigDecimal weightKg;

    @NotNull(message = "距离不能为空")
    @DecimalMin(value = "0.1", message = "距离最小为0.1km")
    private BigDecimal distanceKm;
}
