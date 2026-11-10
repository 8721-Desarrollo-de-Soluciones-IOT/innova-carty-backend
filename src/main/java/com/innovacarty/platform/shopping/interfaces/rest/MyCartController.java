package com.innovacarty.platform.shopping.interfaces.rest;

import static com.innovacarty.platform.shared.interfaces.rest.Errors.notFound;

import com.innovacarty.platform.monitoring.application.CartLifecycleService;
import com.innovacarty.platform.monitoring.domain.model.Cart;
import com.innovacarty.platform.monitoring.domain.model.ShoppingSession;
import com.innovacarty.platform.monitoring.infrastructure.persistence.ShoppingSessionRepository;
import com.innovacarty.platform.payment.domain.model.Payment;
import com.innovacarty.platform.payment.infrastructure.persistence.PaymentRepository;
import com.innovacarty.platform.shared.infrastructure.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** "Mi carrito": what the shopper sees in the mobile app. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "My cart", description = "Shopper view of the mobile app")
@Transactional
public class MyCartController {
    private final CartLifecycleService lifecycle;
    private final ShoppingSessionRepository sessions;
    private final PaymentRepository payments;

    public MyCartController(CartLifecycleService lifecycle, ShoppingSessionRepository sessions, PaymentRepository payments) {
        this.lifecycle = lifecycle;
        this.sessions = sessions;
        this.payments = payments;
    }

    public record LinkRequest(String cartId) {
    }

    public record BudgetRequest(Double budget) {
    }

    /** Cart and session linked to the app. */
    public record MyCartResource(Cart cart, ShoppingSession session) {
    }

    @GetMapping("/cart")
    @Transactional(readOnly = true)
    @Operation(summary = "Live state of the cart linked to my app (404 if none)")
    public MyCartResource getMyCart(@RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        ShoppingSession session = mySession(user);
        return new MyCartResource(lifecycle.getCart(session.getCartId()), session);
    }

    @PostMapping("/cart")
    @Operation(summary = "Link a cart to my app (the id comes from the QR on the cart screen)")
    public MyCartResource link(@RequestBody LinkRequest request, @RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        ShoppingSession session = lifecycle.linkShopper(request.cartId(), user.userId(), user.name());
        return new MyCartResource(lifecycle.getCart(session.getCartId()), session);
    }

    @PatchMapping("/cart")
    @Operation(summary = "Set my budget for this purchase (0 = no budget)")
    public MyCartResource setBudget(@RequestBody BudgetRequest request, @RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        ShoppingSession session = mySession(user);
        Cart cart = lifecycle.getCart(session.getCartId());
        lifecycle.setBudget(cart, session, request.budget() == null ? 0 : request.budget());
        return new MyCartResource(cart, session);
    }

    @DeleteMapping("/cart")
    @Operation(summary = "Unlink the cart from my app")
    public ResponseEntity<Void> unlink(@RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        sessions.findFirstByShopperUserIdAndClosedAtIsNullOrderByStartedAtDesc(user.userId())
                .ifPresent(s -> s.setShopperUserId(null));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    @Transactional(readOnly = true)
    @Operation(summary = "My purchases")
    public List<ShoppingSession> mySessions(@RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        return sessions.findByShopperUserIdOrderByStartedAtDesc(user.userId());
    }

    @GetMapping("/payments")
    @Transactional(readOnly = true)
    @Operation(summary = "My payments (receipts)")
    public List<Payment> myPayments(@RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        return payments.findByUserIdOrderByPaidAtDesc(user.userId());
    }

    private ShoppingSession mySession(CurrentUser user) {
        return sessions.findFirstByShopperUserIdAndClosedAtIsNullOrderByStartedAtDesc(user.userId())
                .orElseThrow(() -> notFound("You have no cart linked"));
    }
}
