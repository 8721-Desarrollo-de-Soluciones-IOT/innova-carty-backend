package com.innovacarty.platform.shared.infrastructure.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Issues and verifies JSON Web Tokens signed with HMAC-SHA256 (HS256). */
@Component
public class TokenService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String HEADER = URL_ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final long ttlSeconds;
    private final ObjectMapper mapper;

    public TokenService(@Value("${innovacarty.jwt.secret}") String secret,
                        @Value("${innovacarty.jwt.ttl-hours}") long ttlHours,
                        ObjectMapper mapper) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlHours * 3600;
        this.mapper = mapper;
    }

    public String issue(Long userId, String role, String name) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(userId));
        claims.put("role", role);
        claims.put("name", name);
        claims.put("iat", now);
        claims.put("exp", now + ttlSeconds);
        try {
            String payload = URL_ENCODER.encodeToString(mapper.writeValueAsBytes(claims));
            String content = HEADER + "." + payload;
            return content + "." + URL_ENCODER.encodeToString(sign(content));
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the token", e);
        }
    }

    public Optional<CurrentUser> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, URL_DECODER.decode(parts[2]))) return Optional.empty();
            Map<String, Object> claims = mapper.readValue(URL_DECODER.decode(parts[1]), new TypeReference<Map<String, Object>>() { });
            long exp = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) return Optional.empty();
            return Optional.of(new CurrentUser(Long.valueOf((String) claims.get("sub")), (String) claims.get("role"), (String) claims.get("name")));
        } catch (RuntimeException | IOException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
