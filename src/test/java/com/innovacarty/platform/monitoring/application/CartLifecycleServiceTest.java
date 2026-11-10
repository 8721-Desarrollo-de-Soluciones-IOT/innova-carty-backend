package com.innovacarty.platform.monitoring.application;

import com.innovacarty.platform.catalog.domain.model.Product;
import com.innovacarty.platform.catalog.infrastructure.persistence.ProductRepository;
import com.innovacarty.platform.monitoring.domain.model.Alert;
import com.innovacarty.platform.monitoring.domain.model.AlertSeverity;
import com.innovacarty.platform.monitoring.domain.model.AlertType;
import com.innovacarty.platform.monitoring.domain.model.Cart;
import com.innovacarty.platform.monitoring.domain.model.CartStatus;
import com.innovacarty.platform.monitoring.domain.model.SessionItem;
import com.innovacarty.platform.monitoring.domain.model.SessionStatus;
import com.innovacarty.platform.monitoring.domain.model.ShoppingSession;
import com.innovacarty.platform.monitoring.infrastructure.persistence.AlertRepository;
import com.innovacarty.platform.monitoring.infrastructure.persistence.AuditLogRepository;
import com.innovacarty.platform.monitoring.infrastructure.persistence.CartEventRepository;
import com.innovacarty.platform.monitoring.infrastructure.persistence.CartRepository;
import com.innovacarty.platform.monitoring.infrastructure.persistence.SessionStatRepository;
import com.innovacarty.platform.monitoring.infrastructure.persistence.ShoppingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests of the domain rules of a cart and its shopping session (TS02, US04, US05, US06, US08). */
@ExtendWith(MockitoExtension.class)
class CartLifecycleServiceTest {
    private static final String MILK_TAG = "04414793C92A81";
    private static final String COOKIES_TAG = "044D4593C92A81";

    @Mock
    private CartRepository carts;
    @Mock
    private ShoppingSessionRepository sessions;
    @Mock
    private AlertRepository alerts;
    @Mock
    private CartEventRepository events;
    @Mock
    private AuditLogRepository auditLogs;
    @Mock
    private SessionStatRepository stats;
    @Mock
    private ProductRepository products;

    @InjectMocks
    private CartLifecycleService lifecycle;

    private Cart cart;

    @BeforeEach
    void setUp() {
        cart = new Cart();
        cart.setId("CART-0427");
        cart.setStatus(CartStatus.AVAILABLE);
        cart.setZone("Aisle 4");
        lenient().when(sessions.save(any())).then(returnsFirstArg());
        lenient().when(products.findAll()).thenReturn(List.of(
                product("SKU-IC-001", "Leche entera 1 L", 4.80, 1030, 8, MILK_TAG),
                product("SKU-IC-003", "Galletas de vainilla", 2.50, 150, 8, COOKIES_TAG)));
    }

    @Nested
    @DisplayName("Shopping session (TS02)")
    class Session {
        @Test
        void startsAnActiveOrderLinkedToTheCart() {
            ShoppingSession session = lifecycle.startSession(cart);

            assertThat(session.getId()).startsWith("ORD-").endsWith("-0427");
            assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(session.getCartId()).isEqualTo("CART-0427");
            assertThat(cart.getSessionId()).isEqualTo(session.getId());
            assertThat(cart.getStatus()).isEqualTo(CartStatus.IN_SESSION);
            assertThat(cart.getTotal()).isZero();
            verify(sessions).save(session);
        }

        @Test
        void finishingAnUnpaidOrderCancelsItAndFreesTheCart() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1));

            lifecycle.finish(cart);

            assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
            assertThat(session.getClosedAt()).isNotNull();
            assertThat(cart.getStatus()).isEqualTo(CartStatus.AVAILABLE);
            assertThat(cart.getSessionId()).isNull();
            assertThat(cart.getItemsCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Items read by RFID and running total (US06, US05)")
    class Items {
        @Test
        void matchesTheTagsWithTheCatalogAndRecomputesTheTotal() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1, COOKIES_TAG, 2));

            assertThat(session.getItems()).extracting(SessionItem::getSku).containsExactlyInAnyOrder("SKU-IC-001", "SKU-IC-003");
            assertThat(cart.getItemsCount()).isEqualTo(3);
            assertThat(cart.getTotal()).isEqualTo(9.80);
            assertThat(session.getTotal()).isEqualTo(9.80);
            assertThat(session.getExpectedWeight()).isEqualTo(1030 + 2 * 150);
            assertThat(session.getWeightTolerance()).isEqualTo(106); // 8 % of 1 330 g
        }

        @Test
        void ignoresTagsThatAreNotInTheCatalog() {
            openSessionWith(Map.of(MILK_TAG, 1, "04FFFFFFFFFFFF", 1));

            assertThat(cart.getItemsCount()).isEqualTo(1);
            assertThat(cart.getTotal()).isEqualTo(4.80);
        }

        @Test
        void removesAnItemWhenItsTagIsNoLongerRead() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1, COOKIES_TAG, 2));

            lifecycle.syncItems(cart, session, Map.of(COOKIES_TAG, 2));

            assertThat(session.getItems()).extracting(SessionItem::getSku).containsExactly("SKU-IC-003");
            assertThat(cart.getTotal()).isEqualTo(5.00);
        }

        @Test
        void theLongestRfidPrefixWins() {
            Product family = product("SKU-1", "Family", 1, 100, 5, "E2003412");
            Product exact = product("SKU-2", "Exact", 1, 100, 5, "E200 3412 0127");

            assertThat(CartLifecycleService.matchProduct(List.of(family, exact), "e2-00-34-12-01-27-99")).isSameAs(exact);
            assertThat(CartLifecycleService.matchProduct(List.of(family, exact), "E2003412FFFF")).isSameAs(family);
            assertThat(CartLifecycleService.matchProduct(List.of(family, exact), "0400")).isNull();
        }
    }

    @Nested
    @DisplayName("Weight check with the load cell (US06)")
    class Weight {
        @Test
        void pausesThePaymentWhenTheWeightIsOutOfTolerance() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1, COOKIES_TAG, 2));

            lifecycle.checkWeight(cart, session, 1700);

            assertThat(cart.getStatus()).isEqualTo(CartStatus.DISCREPANCY);
            assertThat(cart.getLastEvent()).isEqualTo("+370 g without tag");
            Alert alert = savedAlert();
            assertThat(alert.getType()).isEqualTo(AlertType.WEIGHT);
            assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.WARNING);
        }

        @Test
        void acceptsADifferenceInsideTheTolerance() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1, COOKIES_TAG, 2));

            lifecycle.checkWeight(cart, session, 1330 + 100);

            assertThat(cart.getStatus()).isEqualTo(CartStatus.IN_SESSION);
            verify(alerts, never()).save(any());
        }

        @Test
        void goesBackToTheSessionWhenTheWeightIsFixed() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1, COOKIES_TAG, 2));
            lifecycle.checkWeight(cart, session, 1700);

            lifecycle.checkWeight(cart, session, 1330);

            assertThat(cart.getStatus()).isEqualTo(CartStatus.IN_SESSION);
            assertThat(cart.getLastEvent()).isEqualTo("Weight back within tolerance");
        }
    }

    @Nested
    @DisplayName("Budget (US04)")
    class Budget {
        @Test
        void savesTheBudgetOnTheSessionAndTheCart() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1));

            lifecycle.setBudget(cart, session, 50);

            assertThat(session.getBudget()).isEqualTo(50);
            assertThat(cart.getBudget()).isEqualTo(50);
        }

        @Test
        void rejectsANegativeBudget() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1));

            assertStatus(() -> lifecycle.setBudget(cart, session, -1), HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("Payment and exit (US08)")
    class Payment {
        @Test
        void aCartWithItemsCanBePaid() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1));

            assertThat(lifecycle.payableSession(cart)).isSameAs(session);
        }

        @Test
        void anEmptyCartCannotBePaid() {
            openSessionWith(Map.of());

            assertStatus(() -> lifecycle.payableSession(cart), HttpStatus.CONFLICT);
        }

        @Test
        void aCartWithAWeightDiscrepancyCannotBePaid() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1));
            lifecycle.checkWeight(cart, session, 3000);

            assertStatus(() -> lifecycle.payableSession(cart), HttpStatus.CONFLICT);
        }

        @Test
        void markingThePaymentMovesTheOrderAndTheCartToPaid() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1));

            lifecycle.markPaid(cart, session, "Yape", "12345678");

            assertThat(session.getStatus()).isEqualTo(SessionStatus.PAID);
            assertThat(session.getPaymentMethod()).isEqualTo("Yape");
            assertThat(cart.getStatus()).isEqualTo(CartStatus.PAID);
            assertStatus(() -> lifecycle.payableSession(cart), HttpStatus.CONFLICT);
        }

        @Test
        void locksTheWheelsWhenAnUnpaidCartReachesTheExit() {
            openSessionWith(Map.of(MILK_TAG, 1));

            lifecycle.lockAtExit(cart);

            assertThat(cart.getStatus()).isEqualTo(CartStatus.LOCKED);
            Alert alert = savedAlert();
            assertThat(alert.getType()).isEqualTo(AlertType.GEOFENCE);
            assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        }

        @Test
        void aPaidCartLeavesWithoutBeingLocked() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1));
            lifecycle.markPaid(cart, session, "Plin", "87654321");

            lifecycle.lockAtExit(cart);

            assertThat(cart.getStatus()).isEqualTo(CartStatus.PAID);
            verify(alerts, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Link the shopper app (US05)")
    class Link {
        @Test
        void linksTheShopperToTheOpenSession() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1));
            when(carts.findById("CART-0427")).thenReturn(Optional.of(cart));

            ShoppingSession linked = lifecycle.linkShopper("cart-0427", 7L, "Camila R.");

            assertThat(linked).isSameAs(session);
            assertThat(session.getShopperUserId()).isEqualTo(7L);
            assertThat(session.getShopper()).isEqualTo("Camila R.");
        }

        @Test
        void rejectsACartAlreadyLinkedToAnotherShopper() {
            ShoppingSession session = openSessionWith(Map.of(MILK_TAG, 1));
            session.setShopperUserId(8L);
            when(carts.findById("CART-0427")).thenReturn(Optional.of(cart));

            assertStatus(() -> lifecycle.linkShopper("CART-0427", 7L, "Camila R."), HttpStatus.CONFLICT);
        }

        @Test
        void rejectsAnOfflineCart() {
            cart.setStatus(CartStatus.OFFLINE);
            when(carts.findById("CART-0427")).thenReturn(Optional.of(cart));

            assertStatus(() -> lifecycle.linkShopper("CART-0427", 7L, "Camila R."), HttpStatus.CONFLICT);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Opens a session on the cart and loads the tags read by RFID. */
    private ShoppingSession openSessionWith(Map<String, Integer> tags) {
        ShoppingSession session = lifecycle.startSession(cart);
        lenient().when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        lifecycle.syncItems(cart, session, new LinkedHashMap<>(tags));
        return session;
    }

    private Alert savedAlert() {
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alerts).save(captor.capture());
        return captor.getValue();
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status));
    }

    private static Product product(String id, String name, double price, int grams, double tolerance, String tag) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(price);
        product.setNominalWeight(grams);
        product.setTolerance(tolerance);
        product.setRfidPrefix(tag);
        return product;
    }
}
