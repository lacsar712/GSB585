package com.citycourier.dto;

import com.citycourier.entity.OrderStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateStatusRequest {

    @NotNull(message = "目标状态不能为空")
    private OrderStatus targetStatus;

    @DecimalMin(value = "0.0", message = "实际费用不能小于0")
    private BigDecimal actualFee;
}
