package com.innovacarty.platform.iam.application;

import static com.innovacarty.platform.shared.interfaces.rest.Errors.conflict;
import static com.innovacarty.platform.shared.interfaces.rest.Errors.forbidden;
import static com.innovacarty.platform.shared.interfaces.rest.Errors.unauthorized;

import com.innovacarty.platform.iam.domain.model.User;
import com.innovacarty.platform.iam.domain.model.UserRole;
import com.innovacarty.platform.iam.infrastructure.persistence.UserRepository;
import com.innovacarty.platform.iam.interfaces.rest.resources.AuthenticatedUserResource;
import com.innovacarty.platform.shared.infrastructure.security.PasswordHasher;
import com.innovacarty.platform.shared.infrastructure.security.TokenService;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Identity &amp; Access: sign-in for staff and shoppers, shopper sign-up and supervisor PIN. */
@Service
@Transactional
public class AuthService {
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordHasher hasher, TokenService tokens) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResource signIn(String email, String password) {
        User user = users.findByEmailIgnoreCase(clean(email))
                .filter(u -> hasher.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> unauthorized("Invalid email or password"));
        return toResource(user);
    }

    /** Self sign-up from the mobile app: always a SHOPPER account. */
    public AuthenticatedUserResource signUp(String fullName, String email, String password) {
        if (fullName == null || fullName.isBlank()) throw conflict("Write your name");
        if (email == null || !email.contains("@")) throw conflict("Write a valid email");
        if (password == null || password.length() < 6) throw conflict("The password needs at least 6 characters");
        if (users.findByEmailIgnoreCase(clean(email)).isPresent()) throw conflict("That email already has an account");
        User user = new User();
        user.setEmail(clean(email));
        user.setPasswordHash(hasher.hash(password));
        user.setFullName(fullName.trim());
        user.setRole(UserRole.SHOPPER);
        user.setJobTitle("Shopper");
        user.setStoreId(1L);
        return toResource(users.save(user));
    }

    @Transactional(readOnly = true)
    public void verifySupervisorPin(Long userId, String pin) {
        User user = users.findById(userId == null ? -1L : userId).orElseThrow(() -> forbidden("Wrong PIN"));
        if (user.getRole() != UserRole.ADMIN || user.getSupervisorPin() == null || !user.getSupervisorPin().equals(pin)) {
            throw forbidden("Wrong PIN");
        }
    }

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> unauthorized("Account not found"));
    }

    private AuthenticatedUserResource toResource(User user) {
        String token = tokens.issue(user.getId(), user.getRole().name(), user.getFullName());
        return new AuthenticatedUserResource(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(),
                user.getJobTitle(), user.getStoreId(), token);
    }

    private static String clean(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
