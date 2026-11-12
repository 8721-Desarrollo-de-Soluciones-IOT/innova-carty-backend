package com.innovacarty.platform.payment.interfaces.rest;

import com.innovacarty.platform.payment.application.PaymentService;
import com.innovacarty.platform.payment.application.PaymentService.WebhookOutcome;
import com.innovacarty.platform.payment.domain.model.Payment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TS03 - Webhook that the payment provider (Yape, Plin or the card gateway) calls when a payment
 * is confirmed or rejected. It is protected with the shared secret in the X-Webhook-Secret header.
 */
@RestController
@RequestMapping("/api/v1/payments/webhook")
@Tag(name = "Payment Webhook", description = "Confirmation of external payments sent by the provider (X-Webhook-Secret)")
public class PaymentWebhookController {
    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * @param eventId       unique id of the provider notification (repeated ids are ignored)
     * @param provider      YAPE, PLIN or CARD
     * @param operationCode operation number given by the provider
     * @param amount        amount paid, must match the order total
     * @param status        APPROVED or REJECTED
     */
    public record PaymentWebhookRequest(String eventId, String provider, String cartId, String operationCode,
                                        Double amount, String status) {
    }

    public record PaymentWebhookResponse(boolean received, String result, Payment payment) {
    }

    @PostMapping
    @Operation(summary = "Receive the confirmation of an external payment. APPROVED pays the open order of the cart "
            + "(the cart, the console and the app switch to PAID); REJECTED is saved in the cart history; "
            + "a repeated eventId does not create a second payment")
    public PaymentWebhookResponse receive(@RequestBody PaymentWebhookRequest request) {
        WebhookOutcome outcome = paymentService.confirmExternal(request.eventId(), request.provider(), request.cartId(),
                request.operationCode(), request.amount(), request.status());
        return new PaymentWebhookResponse(true, outcome.result().name(), outcome.payment());
    }
}
