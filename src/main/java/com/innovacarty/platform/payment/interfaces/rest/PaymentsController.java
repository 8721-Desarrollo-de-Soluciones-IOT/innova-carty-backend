package com.innovacarty.platform.payment.interfaces.rest;

import com.innovacarty.platform.payment.application.PaymentService;
import com.innovacarty.platform.payment.domain.model.Payment;
import com.innovacarty.platform.payment.infrastructure.persistence.PaymentRepository;
import com.innovacarty.platform.shared.infrastructure.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Simulated Yape / Plin / card payments")
public class PaymentsController {
    private final PaymentService paymentService;
    private final PaymentRepository payments;

    public PaymentsController(PaymentService paymentService, PaymentRepository payments) {
        this.paymentService = paymentService;
        this.payments = payments;
    }

    public record PayRequest(String cartId, String method) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pay the open session of a cart (method YAPE, PLIN or CARD). The cart switches to PAID")
    public Payment pay(@RequestBody PayRequest request, @RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        return paymentService.pay(request.cartId(), request.method(), user.userId(), !user.isAdmin());
    }

    @GetMapping
    @Operation(summary = "Payments of the store (admin), optionally of one cart")
    public List<Payment> getAll(@RequestParam(required = false) String cartId) {
        if (cartId != null && !cartId.isBlank()) return payments.findByCartIdOrderByPaidAtDesc(cartId);
        return payments.findAllByOrderByPaidAtDesc();
    }
}
