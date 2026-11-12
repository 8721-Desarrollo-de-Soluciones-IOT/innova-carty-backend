package com.innovacarty.platform.payment.application;

import static com.innovacarty.platform.shared.interfaces.rest.Errors.badRequest;
import static com.innovacarty.platform.shared.interfaces.rest.Errors.conflict;
import static com.innovacarty.platform.shared.interfaces.rest.Errors.forbidden;

import com.innovacarty.platform.monitoring.application.CartLifecycleService;
import com.innovacarty.platform.monitoring.domain.model.Cart;
import com.innovacarty.platform.monitoring.domain.model.EventTone;
import com.innovacarty.platform.monitoring.domain.model.ShoppingSession;
import com.innovacarty.platform.payment.domain.model.Payment;
import com.innovacarty.platform.payment.infrastructure.persistence.PaymentRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment &amp; Checkout. Payments are simulated: the app shows the Yape / Plin / card screen and
 * this service approves them, so the cart, the console and the app move to PAID at the same time.
 */
@Service
@Transactional
public class PaymentService {
    /** Accepted methods and the label shown to people. CARD_RFID is the card tapped on the cart reader. */
    public static final Map<String, String> METHODS = Map.of(
            "YAPE", "Yape",
            "PLIN", "Plin",
            "CARD", "Card",
            "CARD_RFID", "Card on cart");

    private final PaymentRepository payments;
    private final CartLifecycleService lifecycle;
    private final SecureRandom random = new SecureRandom();

    public PaymentService(PaymentRepository payments, CartLifecycleService lifecycle) {
        this.payments = payments;
        this.lifecycle = lifecycle;
    }

    /**
     * @param userId     who pays (null when the card is tapped on the cart)
     * @param mustOwnCart true for shoppers: they can only pay the cart linked to their app
     */
    public Payment pay(String cartId, String method, Long userId, boolean mustOwnCart) {
        String key = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.containsKey(key)) throw badRequest("Payment method must be YAPE, PLIN or CARD");

        Cart cart = lifecycle.getCart(cartId);
        ShoppingSession session = lifecycle.payableSession(cart);
        if (mustOwnCart && (session.getShopperUserId() == null || !session.getShopperUserId().equals(userId))) {
            throw forbidden("Link this cart to your app before paying");
        }

        return approve(cart, session, key, String.format("%08d", random.nextInt(100_000_000)),
                userId != null ? userId : session.getShopperUserId(), null);
    }

    /** What the webhook did with a notification of the payment provider. */
    public enum WebhookResult { PAYMENT_CREATED, DUPLICATE, REJECTION_RECORDED }

    public record WebhookOutcome(WebhookResult result, Payment payment) {
    }

    /**
     * TS03 - confirmation sent by the payment provider (Yape, Plin or card gateway).
     * The same eventId is processed only once, because providers retry their notifications.
     */
    public WebhookOutcome confirmExternal(String eventId, String provider, String cartId, String operationCode,
                                          Double amount, String status) {
        if (eventId == null || eventId.isBlank()) throw badRequest("eventId is required");
        if (cartId == null || cartId.isBlank()) throw badRequest("cartId is required");
        String key = provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.containsKey(key) || "CARD_RFID".equals(key)) throw badRequest("provider must be YAPE, PLIN or CARD");
        String result = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);

        Optional<Payment> previous = payments.findByExternalEventId(eventId.trim());
        if (previous.isPresent()) return new WebhookOutcome(WebhookResult.DUPLICATE, previous.get());

        Cart cart = lifecycle.getCart(cartId);
        if ("REJECTED".equals(result) || "FAILED".equals(result)) {
            lifecycle.record(cart.getId(), "PAYMENT", "Payment rejected · " + METHODS.get(key),
                    "Provider event " + eventId.trim(), EventTone.WARNING);
            return new WebhookOutcome(WebhookResult.REJECTION_RECORDED, null);
        }
        if (!"APPROVED".equals(result)) throw badRequest("status must be APPROVED or REJECTED");

        ShoppingSession session = lifecycle.payableSession(cart);
        if (amount == null || Math.abs(amount - session.getTotal()) > 0.009) {
            throw conflict(String.format(Locale.US, "The amount does not match the order total (S/ %.2f)", session.getTotal()));
        }
        String code = operationCode == null || operationCode.isBlank()
                ? String.format("%08d", random.nextInt(100_000_000)) : operationCode.trim();
        Payment payment = approve(cart, session, key, code, session.getShopperUserId(), eventId.trim());
        return new WebhookOutcome(WebhookResult.PAYMENT_CREATED, payment);
    }

    private Payment approve(Cart cart, ShoppingSession session, String method, String operationCode, Long userId,
                            String externalEventId) {
        Payment payment = new Payment();
        payment.setOperationCode(operationCode);
        payment.setCartId(cart.getId());
        payment.setSessionId(session.getId());
        payment.setUserId(userId);
        payment.setMethod(method);
        payment.setAmount(session.getTotal());
        payment.setStatus("APPROVED");
        payment.setPaidAt(Instant.now());
        payment.setExternalEventId(externalEventId);
        payments.save(payment);

        lifecycle.markPaid(cart, session, METHODS.get(method), operationCode);
        return payment;
    }
}
