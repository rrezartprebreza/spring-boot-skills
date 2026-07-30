package com.example.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.access-token-expiration:900000}")   // 15 minutes
    private long accessTokenExpiration;

    @Value("${app.jwt.refresh-token-expiration:604800000}") // 7 days
    private long refreshTokenExpiration;

    public String generateAccessToken(UserDetails userDetails) {
        return buildToken(Map.of("type", "access"), userDetails, accessTokenExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(Map.of("type", "refresh"), userDetails, refreshTokenExpiration);
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractClaims(token);
        return "access".equals(claims.get("type", String.class))
            && userDetails.getUsername().equals(claims.getSubject())
            && !claims.getExpiration().before(new Date());
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        Instant now = Instant.now();
        return Jwts.builder()
            .claims(extraClaims)
            .subject(userDetails.getUsername())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expiration)))
            .signWith(getSigningKey())
            .compact();
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
