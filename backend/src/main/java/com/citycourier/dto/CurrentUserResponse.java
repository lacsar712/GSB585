package com.citycourier.dto;

import com.citycourier.entity.RoleType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CurrentUserResponse {
    private Long id;
    private String username;
    private RoleType role;
}
