package com.mentoai.mentoai.security;

import com.mentoai.mentoai.config.AuthProperties;
import com.mentoai.mentoai.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final AuthProperties.Jwt jwtProperties;
    private SecretKey secretKey;

    private SecretKey secretKey() {
        if (secretKey == null) {
            secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        }
        return secretKey;
    }

    public String generateAccessToken(UserEntity user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiry = now.plusMinutes(jwtProperties.accessTokenMinutes());
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("provider", user.getAuthProvider().name())
                .setIssuedAt(Date.from(now.toInstant()))
                .setExpiration(Date.from(expiry.toInstant()))
                .signWith(secretKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public long getAccessTokenExpirySeconds() {
        return jwtProperties.accessTokenMinutes() * 60;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(secretKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public Long extractUserId(String token) {
        Jws<Claims> jws = Jwts.parserBuilder().setSigningKey(secretKey()).build().parseClaimsJws(token);
        return Long.valueOf(jws.getBody().getSubject());
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString() + UUID.randomUUID();
    }

    public OffsetDateTime calculateRefreshTokenExpiry() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(jwtProperties.refreshTokenDays());
    }
}
