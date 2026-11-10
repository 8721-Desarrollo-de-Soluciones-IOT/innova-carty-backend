package com.innovacarty.platform.payment.application;

import static com.innovacarty.platform.shared.interfaces.rest.Errors.badRequest;
import static com.innovacarty.platform.shared.interfaces.rest.Errors.forbidden;

import com.innovacarty.platform.monitoring.application.CartLifecycleService;
import com.innovacarty.platform.monitoring.domain.model.Cart;
import com.innovacarty.platform.monitoring.domain.model.ShoppingSession;
import com.innovacarty.platform.payment.domain.model.Payment;
import com.innovacarty.platform.payment.infrastructure.persistence.PaymentRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
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

        Payment payment = new Payment();
        payment.setOperationCode(String.format("%08d", random.nextInt(100_000_000)));
        payment.setCartId(cart.getId());
        payment.setSessionId(session.getId());
        payment.setUserId(userId != null ? userId : session.getShopperUserId());
        payment.setMethod(key);
        payment.setAmount(session.getTotal());
        payment.setStatus("APPROVED");
        payment.setPaidAt(Instant.now());
        payments.save(payment);

        lifecycle.markPaid(cart, session, METHODS.get(key), payment.getOperationCode());
        return payment;
    }
}
