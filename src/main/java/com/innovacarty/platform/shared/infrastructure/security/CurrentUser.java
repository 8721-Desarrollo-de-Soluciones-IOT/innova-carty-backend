package com.innovacarty.platform.shared.infrastructure.security;

/** User behind the bearer token of the current request. */
public record CurrentUser(Long userId, String role, String name) {
    /** Request attribute where {@link AuthInterceptor} leaves the current user. */
    public static final String ATTRIBUTE = "innovacarty.currentUser";

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
