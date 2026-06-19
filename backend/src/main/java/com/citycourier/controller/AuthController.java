package com.citycourier.controller;

import com.citycourier.dto.*;
import com.citycourier.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("登录成功", authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok("退出登录成功", null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.ok("获取当前用户成功", authService.currentUser());
    }

    @GetMapping("/riders")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    public ApiResponse<List<RiderOptionResponse>> riders() {
        return ApiResponse.ok("获取骑手列表成功", authService.riderUsernames());
    }
}
