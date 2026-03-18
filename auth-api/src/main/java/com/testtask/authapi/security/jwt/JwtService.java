package com.testtask.authapi.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/*  The token factory + validator */

@Component
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;
    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public String generateAuthToken(String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .expiration(new Date(now.getTime() + jwtExpirationMs))
                .signWith(getSingInKey())
                .compact();
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSingInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public boolean validateJwtToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSingInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return true;
        }catch (ExpiredJwtException expEx){
            log.error("Expired JwtException", expEx);
        }catch (UnsupportedJwtException expEx){
            log.error("Unsupported JwtException", expEx);
        }catch (MalformedJwtException expEx){
            log.error("Malformed JwtException", expEx);
        }catch (SecurityException expEx){
            log.error("Security Exception", expEx);
        }catch (Exception expEx){
            log.error("invalid token", expEx);
        }
        return false;
    }

    private SecretKey getSingInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
