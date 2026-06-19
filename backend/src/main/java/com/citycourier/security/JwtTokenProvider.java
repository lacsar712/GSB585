package com.citycourier.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expire-seconds}")
    private long expireSeconds;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT_SECRET 未配置，服务无法启动");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 长度必须至少为 32 个字符");
        }
        secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expireSeconds);

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("role", userDetails.getRole().name())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsername(String token) {
        return parse(token).getBody().getSubject();
    }

    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
    }
}
