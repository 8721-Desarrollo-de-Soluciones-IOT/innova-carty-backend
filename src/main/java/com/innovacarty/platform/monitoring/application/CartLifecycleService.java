package com.innovacarty.platform.monitoring.application;

import static com.innovacarty.platform.shared.interfaces.rest.Errors.conflict;
import static com.innovacarty.platform.shared.interfaces.rest.Errors.notFound;

import com.innovacarty.platform.catalog.domain.model.Product;
import com.innovacarty.platform.catalog.infrastructure.persistence.ProductRepository;
import com.innovacarty.platform.monitoring.domain.model.*;
import com.innovacarty.platform.monitoring.infrastructure.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain rules of a cart and its shopping session: start, items read by RFID, weight check,
 * geofence lock, supervisor unlock, payment and exit. Shared by the device gateway (ESP32),
 * the Web Console and the mobile app so every client sees the same state.
 */
@Service
@Transactional
public class CartLifecycleService {
    public static final ZoneId STORE_ZONE = ZoneId.of("America/Lima");
    private static final DateTimeFormatter ORDER_ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MIN_WEIGHT_TOLERANCE = 30;
    private static final double DEFAULT_TOLERANCE_PERCENT = 5;

    private final CartRepository carts;
    private final ShoppingSessionRepository sessions;
    private final AlertRepository alerts;
    private final CartEventRepository events;
    private final AuditLogRepository auditLogs;
    private final SessionStatRepository stats;
    private final ProductRepository products;

    public CartLifecycleService(CartRepository carts, ShoppingSessionRepository sessions, AlertRepository alerts,
                                CartEventRepository events, AuditLogRepository auditLogs, SessionStatRepository stats,
                                ProductRepository products) {
        this.carts = carts;
        this.sessions = sessions;
        this.alerts = alerts;
        this.events = events;
        this.auditLogs = auditLogs;
        this.stats = stats;
        this.products = products;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Cart getCart(String cartId) {
        return carts.findById(normalizeCartId(cartId)).orElseThrow(() -> notFound("Cart " + cartId + " not found"));
    }

    /** Session of the cart that has not been closed yet (ACTIVE or PAID). */
    @Transactional(readOnly = true)
    public Optional<ShoppingSession> openSession(Cart cart) {
        if (cart.getSessionId() == null) return Optional.empty();
        return sessions.findById(cart.getSessionId()).filter(s -> s.getClosedAt() == null);
    }

    public Cart findOrRegisterCart(String cartId) {
        String id = normalizeCartId(cartId);
        return carts.findById(id).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setId(id);
            cart.setStatus(CartStatus.AVAILABLE);
            cart.setZone("Entrance");
            cart.setBattery(100);
            cart.setStoreId(1L);
            touch(cart, "Cart registered");
            Cart saved = carts.save(cart);
            record(id, "DEVICE", "Cart registered", "First connection of " + id, EventTone.INFO);
            return saved;
        });
    }

    // ------------------------------------------------------------------ session

    public ShoppingSession startSession(Cart cart) {
        Instant now = Instant.now();
        ShoppingSession session = new ShoppingSession();
        String digits = cart.getId().replaceAll("\\D", "");
        session.setId("ORD-" + LocalDateTime.now(STORE_ZONE).format(ORDER_ID) + (digits.isEmpty() ? "" : "-" + digits));
        session.setCartId(cart.getId());
        session.setShopper("Guest");
        session.setStatus(SessionStatus.ACTIVE);
        session.setStartedAt(now);
        // The id is assigned, so save() merges: keep working on the managed copy or the changes are lost
        session = sessions.save(session);

        cart.setSessionId(session.getId());
        cart.setStatus(CartStatus.IN_SESSION);
        cart.setItemsCount(0);
        cart.setTotal(0);
        cart.setBudget(0);
        touch(cart, "Shopping session started");
        countSession(now);
        record(cart.getId(), "SESSION", "Session " + session.getId() + " started", "Shopper took the cart", EventTone.INFO);
        return session;
    }

    /** Replaces the items of the session with the tags the cart has read (tag UID -> quantity). */
    public void syncItems(Cart cart, ShoppingSession session, Map<String, Integer> tagQuantities) {
        if (session.getStatus() != SessionStatus.ACTIVE) return;
        List<Product> catalog = products.findAll();
        Map<String, Double> tolerances = new HashMap<>();
        catalog.forEach(p -> tolerances.put(p.getId(), p.getTolerance()));

        Map<String, String> names = new HashMap<>();
        Map<String, Integer> before = new LinkedHashMap<>();
        for (SessionItem item : session.getItems()) {
            before.merge(item.getSku(), item.getQuantity(), Integer::sum);
            names.put(item.getSku(), item.getName());
        }

        Map<String, SessionItem> bySku = new LinkedHashMap<>();
        tagQuantities.forEach((tag, quantity) -> {
            if (quantity == null || quantity <= 0) return;
            Product product = matchProduct(catalog, tag);
            if (product == null) return; // the cart reports unknown tags as UNKNOWN_TAG events
            SessionItem item = bySku.get(product.getId());
            if (item != null) {
                item.setQuantity(item.getQuantity() + quantity);
                return;
            }
            item = new SessionItem();
            item.setSku(product.getId());
            item.setName(product.getName());
            item.setRfidTag(normalizeTag(tag));
            item.setQuantity(quantity);
            item.setUnitPrice(product.getPrice());
            item.setNominalWeight(product.getNominalWeight());
            bySku.put(product.getId(), item);
            names.put(product.getId(), product.getName());
        });

        Map<String, Integer> after = new LinkedHashMap<>();
        bySku.values().forEach(i -> after.put(i.getSku(), i.getQuantity()));
        if (after.equals(before)) return;

        session.getItems().clear();
        session.getItems().addAll(bySku.values());
        recompute(cart, session, tolerances);

        String change = describeChange(before, after, names);
        touch(cart, change);
        record(cart.getId(), "ITEM", change, String.format(Locale.US, "%d items · S/ %.2f", cart.getItemsCount(), cart.getTotal()), EventTone.INFO);
    }

    /** Compares the stable reading of the load cells with the nominal weight of the items read by RFID. */
    public void checkWeight(Cart cart, ShoppingSession session, int measuredWeight) {
        if (session.getStatus() != SessionStatus.ACTIVE) return;
        session.setMeasuredWeight(measuredWeight);
        int difference = measuredWeight - session.getExpectedWeight();
        boolean withinTolerance = Math.abs(difference) <= Math.max(MIN_WEIGHT_TOLERANCE, session.getWeightTolerance());

        if (!withinTolerance && cart.getStatus() == CartStatus.IN_SESSION) {
            cart.setStatus(CartStatus.DISCREPANCY);
            String label = (difference > 0 ? "+" + difference + " g without tag" : difference + " g missing");
            touch(cart, label);
            raise(cart, AlertType.WEIGHT, AlertSeverity.WARNING, "Weight discrepancy · payment paused",
                    String.format(Locale.US, "%s measured %d g, expected %d g (±%d g).", cart.getId(), measuredWeight,
                            session.getExpectedWeight(), Math.max(MIN_WEIGHT_TOLERANCE, session.getWeightTolerance())));
            record(cart.getId(), "WEIGHT", "Weight " + label, "Payment paused", EventTone.WARNING);
        } else if (withinTolerance && cart.getStatus() == CartStatus.DISCREPANCY) {
            cart.setStatus(CartStatus.IN_SESSION);
            resolveAlerts(cart.getId(), AlertType.WEIGHT);
            touch(cart, "Weight back within tolerance");
            record(cart.getId(), "WEIGHT", "Weight back within tolerance", measuredWeight + " g", EventTone.OK);
        }
    }

    // ------------------------------------------------------------------ security

    public void lockAtExit(Cart cart) {
        if (cart.getStatus() == CartStatus.PAID || cart.getStatus() == CartStatus.LOCKED) return;
        cart.setStatus(CartStatus.LOCKED);
        String where = cart.getZone() == null ? "the exit" : cart.getZone();
        touch(cart, "Geofence breach at " + where);
        raise(cart, AlertType.GEOFENCE, AlertSeverity.CRITICAL, "Geofence breach · wheels locked",
                String.format(Locale.US, "%s reached %s with order unpaid (S/ %.2f, %d items).", cart.getId(), where, cart.getTotal(), cart.getItemsCount()));
        record(cart.getId(), "GEOFENCE", "Wheels locked at " + where, "Order unpaid", EventTone.CRITICAL);
    }

    /** Supervisor unlock (console, mobile admin view or key fob on the cart). */
    public Cart unlock(Cart cart, String resolution, String note, Long supervisorId, String supervisorName) {
        Optional<ShoppingSession> session = openSession(cart);
        if (session.isEmpty()) cart.setStatus(CartStatus.AVAILABLE);
        else if (session.get().getStatus() == SessionStatus.PAID) cart.setStatus(CartStatus.PAID);
        else cart.setStatus(CartStatus.IN_SESSION);
        if (session.isPresent() && session.get().getStatus() == SessionStatus.ACTIVE) {
            session.get().setMeasuredWeight(session.get().getExpectedWeight());
        }
        resolveAlerts(cart.getId(), null);
        String title = "Unlocked by " + (supervisorName == null ? "supervisor" : supervisorName);
        touch(cart, title);
        record(cart.getId(), "UNLOCK", title, resolution, EventTone.OK);

        AuditLog log = new AuditLog();
        log.setCartId(cart.getId());
        log.setAction("UNLOCK");
        log.setResolution(resolution);
        log.setNote(note == null ? "" : note);
        log.setUserId(supervisorId == null ? 0L : supervisorId);
        log.setCreatedAt(Instant.now());
        auditLogs.save(log);
        return cart;
    }

    // ------------------------------------------------------------------ payment and exit

    /** Checks that the open session of the cart can be paid and returns it. */
    @Transactional(readOnly = true)
    public ShoppingSession payableSession(Cart cart) {
        ShoppingSession session = openSession(cart).orElseThrow(() -> conflict("This cart has no active shopping session"));
        if (session.getStatus() == SessionStatus.PAID) throw conflict("This order is already paid");
        if (cart.getStatus() == CartStatus.DISCREPANCY) throw conflict("Payment paused: weight discrepancy, ask a supervisor");
        if (cart.getStatus() == CartStatus.LOCKED) throw conflict("The cart is locked, ask a supervisor");
        if (session.getItems().isEmpty() || session.getTotal() <= 0) throw conflict("The cart is empty");
        return session;
    }

    public void markPaid(Cart cart, ShoppingSession session, String methodLabel, String operationCode) {
        session.setStatus(SessionStatus.PAID);
        session.setPaidAt(Instant.now());
        session.setPaymentMethod(methodLabel);
        cart.setStatus(CartStatus.PAID);
        touch(cart, String.format(Locale.US, "Paid S/ %.2f with %s", session.getTotal(), methodLabel));
        record(cart.getId(), "PAYMENT", "Payment approved · " + methodLabel,
                String.format(Locale.US, "S/ %.2f · operation %s", session.getTotal(), operationCode), EventTone.OK);
    }

    /** The shopper left the store (or the session was cancelled): the cart is free again. */
    public void finish(Cart cart) {
        Optional<ShoppingSession> session = openSession(cart);
        session.ifPresent(s -> {
            s.setClosedAt(Instant.now());
            if (s.getStatus() == SessionStatus.ACTIVE) s.setStatus(SessionStatus.CANCELLED);
        });
        cart.setSessionId(null);
        cart.setStatus(CartStatus.AVAILABLE);
        cart.setItemsCount(0);
        cart.setTotal(0);
        cart.setBudget(0);
        resolveAlerts(cart.getId(), null);
        touch(cart, "Shopper left the store");
        record(cart.getId(), "EXIT", "Session closed", session.map(ShoppingSession::getId).orElse(""), EventTone.OK);
    }

    // ------------------------------------------------------------------ shopper app

    public ShoppingSession linkShopper(String cartId, Long userId, String name) {
        Cart cart = getCart(cartId);
        if (cart.getStatus() == CartStatus.OFFLINE) throw conflict("The cart is offline");
        sessions.findFirstByShopperUserIdAndClosedAtIsNullOrderByStartedAtDesc(userId)
                .filter(other -> !other.getCartId().equals(cart.getId()))
                .ifPresent(other -> other.setShopperUserId(null));
        ShoppingSession session = openSession(cart).orElseGet(() -> startSession(cart));
        if (session.getShopperUserId() != null && !session.getShopperUserId().equals(userId)) {
            throw conflict("This cart is already linked to another shopper");
        }
        session.setShopperUserId(userId);
        session.setShopper(name);
        touch(cart, name + " linked the app");
        record(cart.getId(), "LINK", "App linked", name, EventTone.INFO);
        return session;
    }

    public void setBudget(Cart cart, ShoppingSession session, double budget) {
        if (budget < 0) throw conflict("The budget cannot be negative");
        session.setBudget(budget);
        cart.setBudget(budget);
        record(cart.getId(), "BUDGET", String.format(Locale.US, "Budget set to S/ %.2f", budget), session.getShopper(), EventTone.INFO);
    }

    // ------------------------------------------------------------------ helpers

    public void touch(Cart cart, String lastEvent) {
        cart.setLastEvent(lastEvent);
        cart.setLastEventAt(Instant.now());
    }

    public CartEvent record(String cartId, String type, String title, String detail, EventTone tone) {
        CartEvent event = new CartEvent();
        event.setCartId(cartId);
        event.setType(type);
        event.setOccurredAt(Instant.now());
        event.setTitle(title);
        event.setDetail(detail == null ? "" : detail);
        event.setTone(tone);
        return events.save(event);
    }

    public Alert raise(Cart cart, AlertType type, AlertSeverity severity, String title, String description) {
        Alert alert = new Alert();
        alert.setCartId(cart.getId());
        alert.setType(type);
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setDescription(description);
        alert.setZone(cart.getZone() == null ? "" : cart.getZone());
        alert.setCreatedAt(Instant.now());
        alert.setStatus(AlertStatus.OPEN);
        return alerts.save(alert);
    }

    public void resolveAlerts(String cartId, AlertType type) {
        for (Alert alert : alerts.findByCartId(cartId)) {
            if (alert.getStatus() != AlertStatus.RESOLVED && (type == null || alert.getType() == type)) {
                alert.setStatus(AlertStatus.RESOLVED);
            }
        }
    }

    private void recompute(Cart cart, ShoppingSession session, Map<String, Double> tolerances) {
        double total = 0;
        int count = 0;
        int expected = 0;
        double tolerance = 0;
        for (SessionItem item : session.getItems()) {
            total += item.getQuantity() * item.getUnitPrice();
            count += item.getQuantity();
            expected += item.getQuantity() * item.getNominalWeight();
            double percent = tolerances.getOrDefault(item.getSku(), DEFAULT_TOLERANCE_PERCENT);
            tolerance += item.getQuantity() * item.getNominalWeight() * percent / 100.0;
        }
        total = Math.round(total * 100) / 100.0;
        session.setTotal(total);
        session.setExpectedWeight(expected);
        session.setWeightTolerance(Math.max(MIN_WEIGHT_TOLERANCE, (int) Math.round(tolerance)));
        cart.setTotal(total);
        cart.setItemsCount(count);
        cart.setBudget(session.getBudget());
    }

    private void countSession(Instant startedAt) {
        String hour = String.format("%02d:00", startedAt.atZone(STORE_ZONE).getHour());
        SessionStat stat = stats.findByHour(hour).orElseGet(() -> {
            SessionStat created = new SessionStat();
            created.setHour(hour);
            return created;
        });
        stat.setSessions(stat.getSessions() + 1);
        stats.save(stat);
    }

    private static String describeChange(Map<String, Integer> before, Map<String, Integer> after, Map<String, String> names) {
        List<String> parts = new ArrayList<>();
        Set<String> skus = new LinkedHashSet<>(before.keySet());
        skus.addAll(after.keySet());
        for (String sku : skus) {
            int diff = after.getOrDefault(sku, 0) - before.getOrDefault(sku, 0);
            if (diff > 0) parts.add("+" + diff + " " + names.getOrDefault(sku, sku));
            else if (diff < 0) parts.add("−" + (-diff) + " " + names.getOrDefault(sku, sku));
        }
        return parts.isEmpty() ? "Items updated" : String.join(", ", parts);
    }

    /** Product whose RFID prefix matches the tag (the longest prefix wins). */
    public static Product matchProduct(List<Product> catalog, String tag) {
        String uid = normalizeTag(tag);
        Product best = null;
        int bestLength = 0;
        for (Product product : catalog) {
            String prefix = normalizeTag(product.getRfidPrefix());
            if (!prefix.isEmpty() && uid.startsWith(prefix) && prefix.length() > bestLength) {
                best = product;
                bestLength = prefix.length();
            }
        }
        return best;
    }

    public static String normalizeTag(String tag) {
        return tag == null ? "" : tag.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
    }

    public static String normalizeCartId(String cartId) {
        return cartId == null ? "" : cartId.trim().toUpperCase(Locale.ROOT);
    }
}
