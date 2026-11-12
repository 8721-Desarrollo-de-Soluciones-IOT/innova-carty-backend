package com.innovacarty.platform.shared.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Protects /api/v1:
 * <ul>
 *   <li>/authentication/** is public (sign-in, sign-up).</li>
 *   <li>/devices/** needs the X-Device-Key header (ESP32 of the carts).</li>
 *   <li>POST /payments/webhook needs the X-Webhook-Secret header (payment provider).</li>
 *   <li>Shoppers can use /me/**, POST /payments and GET /products.</li>
 *   <li>Everything else needs an ADMIN token.</li>
 * </ul>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenService tokens;
    private final String deviceKey;
    private final String webhookSecret;

    public AuthInterceptor(TokenService tokens, @Value("${innovacarty.device.api-key}") String deviceKey,
                           @Value("${innovacarty.payments.webhook-secret}") String webhookSecret) {
        this.tokens = tokens;
        this.deviceKey = deviceKey;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (CorsUtils.isPreFlightRequest(request)) return true;
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.startsWith("/api/v1/authentication/")) return true;
        if (path.startsWith("/api/v1/devices/")) {
            return deviceKey.equals(request.getHeader("X-Device-Key")) || deny(response, 401, "Invalid device key");
        }
        if (path.equals("/api/v1/payments/webhook")) {
            return sameSecret(webhookSecret, request.getHeader("X-Webhook-Secret")) || deny(response, 401, "Invalid webhook secret");
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return deny(response, 401, "Sign in first");
        Optional<CurrentUser> user = tokens.verify(header.substring(7).trim());
        if (user.isEmpty()) return deny(response, 401, "Your session expired, sign in again");
        request.setAttribute(CurrentUser.ATTRIBUTE, user.get());

        if (user.get().isAdmin()) return true;
        boolean shopperAllowed = path.startsWith("/api/v1/me")
                || (path.equals("/api/v1/payments") && "POST".equals(method))
                || (path.startsWith("/api/v1/products") && "GET".equals(method));
        return shopperAllowed || deny(response, 403, "This action needs an administrator account");
    }

    /** Compares in constant time, so the secret cannot be guessed by measuring the response time. */
    private static boolean sameSecret(String expected, String received) {
        if (received == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
        return false;
    }
}
