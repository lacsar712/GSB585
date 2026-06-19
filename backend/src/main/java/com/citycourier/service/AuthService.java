package com.citycourier.service;

import com.citycourier.dto.CurrentUserResponse;
import com.citycourier.dto.LoginRequest;
import com.citycourier.dto.LoginResponse;
import com.citycourier.dto.RiderOptionResponse;
import com.citycourier.entity.RoleType;
import com.citycourier.entity.User;
import com.citycourier.repository.UserRepository;
import com.citycourier.security.CustomUserDetails;
import com.citycourier.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtTokenProvider.createToken(authentication);
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

        return new LoginResponse(
                token,
                "Bearer",
                new LoginResponse.UserInfo(principal.getId(), principal.getUsername(), principal.getRole())
        );
    }

    public CurrentUserResponse currentUser() {
        CustomUserDetails principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return new CurrentUserResponse(principal.getId(), principal.getUsername(), principal.getRole());
    }

    public User getCurrentUserEntity() {
        CustomUserDetails principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findById(principal.getId()).orElseThrow();
    }

    public List<RiderOptionResponse> riderUsernames() {
        return userRepository.findByRoleAndEnabledTrueOrderByUsernameAsc(RoleType.RIDER)
                .stream()
                .map(user -> new RiderOptionResponse(user.getUsername(), resolveRiderDisplayName(user.getUsername())))
                .toList();
    }

    private String resolveRiderDisplayName(String username) {
        Matcher matcher = Pattern.compile("^rider(\\d+)$", Pattern.CASE_INSENSITIVE).matcher(username);
        if (matcher.find()) {
            return "骑手" + matcher.group(1);
        }
        return username;
    }
}
