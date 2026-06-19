package com.citycourier.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignOrderRequest {

    @NotBlank(message = "骑手用户名不能为空")
    private String riderUsername;
}
