package com.innovacarty.platform.iam.interfaces.rest;

import com.innovacarty.platform.iam.application.AuthService;
import com.innovacarty.platform.iam.interfaces.rest.resources.AuthenticatedUserResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/authentication")
@Tag(name = "Authentication", description = "Sign-in, shopper sign-up and supervisor PIN")
public class AuthenticationController {
    private final AuthService auth;

    public AuthenticationController(AuthService auth) {
        this.auth = auth;
    }

    public record SignInRequest(String email, String password) {
    }

    public record SignUpRequest(String fullName, String email, String password) {
    }

    public record VerifyPinRequest(Long userId, String pin) {
    }

    @PostMapping("/sign-in")
    @Operation(summary = "Sign in (staff or shopper). The role in the answer decides the views of the app")
    public AuthenticatedUserResource signIn(@RequestBody SignInRequest request) {
        return auth.signIn(request.email(), request.password());
    }

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a shopper account from the mobile app")
    public AuthenticatedUserResource signUp(@RequestBody SignUpRequest request) {
        return auth.signUp(request.fullName(), request.email(), request.password());
    }

    @PostMapping("/verify-pin")
    @Operation(summary = "Check the supervisor PIN before unlocking a cart (204 ok, 403 wrong PIN)")
    public ResponseEntity<Void> verifyPin(@RequestBody VerifyPinRequest request) {
        auth.verifySupervisorPin(request.userId(), request.pin());
        return ResponseEntity.noContent().build();
    }
}
