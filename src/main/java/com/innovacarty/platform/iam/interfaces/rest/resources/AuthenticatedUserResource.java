package com.innovacarty.platform.iam.interfaces.rest.resources;

/** Signed-in user plus the bearer token. role is ADMIN or SHOPPER. */
public record AuthenticatedUserResource(
        Long id,
        String email,
        String fullName,
        String role,
        String jobTitle,
        Long storeId,
        String token) {
}
