package com.innovacarty.platform.payment.application;

import com.innovacarty.platform.monitoring.application.CartLifecycleService;
import com.innovacarty.platform.monitoring.domain.model.Cart;
import com.innovacarty.platform.monitoring.domain.model.CartStatus;
import com.innovacarty.platform.monitoring.domain.model.SessionStatus;
import com.innovacarty.platform.monitoring.domain.model.ShoppingSession;
import com.innovacarty.platform.payment.application.PaymentService.WebhookOutcome;
import com.innovacarty.platform.payment.application.PaymentService.WebhookResult;
import com.innovacarty.platform.payment.domain.model.Payment;
import com.innovacarty.platform.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests of the simulated Yape / Plin / card payments (US08, TS03). */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    private PaymentRepository payments;
    @Mock
    private CartLifecycleService lifecycle;

    @InjectMocks
    private PaymentService paymentService;

    private Cart cart;
    private ShoppingSession session;

    @BeforeEach
    void setUp() {
        cart = new Cart();
        cart.setId("CART-0427");
        cart.setStatus(CartStatus.IN_SESSION);
        session = new ShoppingSession();
        session.setId("ORD-20261110-103040-0427");
        session.setCartId("CART-0427");
        session.setStatus(SessionStatus.ACTIVE);
        session.setShopperUserId(7L);
        session.setTotal(9.80);
        lenient().when(lifecycle.getCart("CART-0427")).thenReturn(cart);
        lenient().when(lifecycle.payableSession(cart)).thenReturn(session);
    }

    @Test
    void approvesThePaymentOfTheLinkedShopper() {
        Payment payment = paymentService.pay("CART-0427", "yape", 7L, true);

        assertThat(payment.getStatus()).isEqualTo("APPROVED");
        assertThat(payment.getMethod()).isEqualTo("YAPE");
        assertThat(payment.getAmount()).isEqualTo(9.80);
        assertThat(payment.getSessionId()).isEqualTo(session.getId());
        assertThat(payment.getUserId()).isEqualTo(7L);
        assertThat(payment.getOperationCode()).matches("\\d{8}");
        assertThat(payment.getPaidAt()).isNotNull();
        verify(payments).save(payment);
        verify(lifecycle).markPaid(cart, session, "Yape", payment.getOperationCode());
    }

    @Test
    void rejectsAnUnknownPaymentMethod() {
        assertStatus(() -> paymentService.pay("CART-0427", "BITCOIN", 7L, true), HttpStatus.BAD_REQUEST);
        verify(payments, never()).save(any());
    }

    @Test
    void aShopperCannotPayACartLinkedToSomeoneElse() {
        assertStatus(() -> paymentService.pay("CART-0427", "PLIN", 9L, true), HttpStatus.FORBIDDEN);
        verify(lifecycle, never()).markPaid(any(), any(), anyString(), anyString());
    }

    @Test
    void theCardTappedOnTheCartIsChargedToTheLinkedShopper() {
        Payment payment = paymentService.pay("CART-0427", "CARD_RFID", null, false);

        assertThat(payment.getUserId()).isEqualTo(7L);
        verify(lifecycle).markPaid(eq(cart), eq(session), eq("Card on cart"), anyString());
    }

    @Test
    void doesNotChargeACartThatCannotBePaid() {
        lenient().when(lifecycle.payableSession(cart))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Payment paused: weight discrepancy, ask a supervisor"));

        assertStatus(() -> paymentService.pay("CART-0427", "YAPE", 7L, true), HttpStatus.CONFLICT);
        verify(payments, never()).save(any());
    }

    // ------------------------------------------------------------------ TS03 webhook

    @Test
    void theProviderConfirmationPaysTheOrder() {
        WebhookOutcome outcome = paymentService.confirmExternal("evt-001", "plin", "CART-0427", "55501234", 9.80, "approved");

        assertThat(outcome.result()).isEqualTo(WebhookResult.PAYMENT_CREATED);
        assertThat(outcome.payment().getOperationCode()).isEqualTo("55501234");
        assertThat(outcome.payment().getExternalEventId()).isEqualTo("evt-001");
        assertThat(outcome.payment().getUserId()).isEqualTo(7L);
        verify(payments).save(outcome.payment());
        verify(lifecycle).markPaid(cart, session, "Plin", "55501234");
    }

    @Test
    void aRepeatedProviderEventDoesNotPayTwice() {
        Payment previous = new Payment();
        when(payments.findByExternalEventId("evt-001")).thenReturn(Optional.of(previous));

        WebhookOutcome outcome = paymentService.confirmExternal("evt-001", "YAPE", "CART-0427", "1", 9.80, "APPROVED");

        assertThat(outcome.result()).isEqualTo(WebhookResult.DUPLICATE);
        assertThat(outcome.payment()).isSameAs(previous);
        verify(payments, never()).save(any());
        verify(lifecycle, never()).markPaid(any(), any(), anyString(), anyString());
    }

    @Test
    void aRejectedPaymentIsOnlyRecorded() {
        WebhookOutcome outcome = paymentService.confirmExternal("evt-002", "YAPE", "CART-0427", null, null, "REJECTED");

        assertThat(outcome.result()).isEqualTo(WebhookResult.REJECTION_RECORDED);
        verify(lifecycle).record(eq("CART-0427"), eq("PAYMENT"), anyString(), anyString(), any());
        verify(payments, never()).save(any());
    }

    @Test
    void theAmountMustMatchTheOrderTotal() {
        assertStatus(() -> paymentService.confirmExternal("evt-003", "YAPE", "CART-0427", "1", 5.00, "APPROVED"), HttpStatus.CONFLICT);
        verify(payments, never()).save(any());
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status));
    }
}
