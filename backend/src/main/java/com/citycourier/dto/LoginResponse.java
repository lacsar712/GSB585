package com.citycourier.dto;

import com.citycourier.entity.RoleType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;
    private UserInfo user;

    @Getter
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private RoleType role;
    }
}
