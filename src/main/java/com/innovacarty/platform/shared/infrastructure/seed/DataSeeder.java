package com.innovacarty.platform.shared.infrastructure.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovacarty.platform.catalog.domain.model.Product;
import com.innovacarty.platform.catalog.domain.model.Store;
import com.innovacarty.platform.catalog.infrastructure.persistence.ProductRepository;
import com.innovacarty.platform.catalog.infrastructure.persistence.StoreRepository;
import com.innovacarty.platform.iam.domain.model.User;
import com.innovacarty.platform.iam.domain.model.UserRole;
import com.innovacarty.platform.iam.infrastructure.persistence.UserRepository;
import com.innovacarty.platform.monitoring.domain.model.*;
import com.innovacarty.platform.monitoring.infrastructure.persistence.*;
import com.innovacarty.platform.shared.infrastructure.security.PasswordHasher;
import com.innovacarty.platform.shared.interfaces.rest.Patch;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the demo data the first time the API starts on an empty database: the fleet of the
 * Sprint 1 fake API (seed/db.json) plus the physical prototype CART-0427 and its 10 tagged products.
 */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    /** The physical prototype uses CART-0427, so the demo cart with that id is renamed. */
    private static final String PROTOTYPE_CART = "CART-0427";
    private static final String DEMO_CART_RENAMED = "CART-0431";

    /** UID of the NTAG215 stickers of the prototype: id, name, category, price, grams, icon, tag. */
    private static final Object[][] PROTOTYPE_PRODUCTS = {
            {"SKU-IC-001", "Leche entera 1 L", "Dairy", 4.80, 1030, "water_full", "04414793C92A81"},
            {"SKU-IC-002", "Arroz extra 1 kg", "Grocery", 5.90, 1000, "rice_bowl", "04464793C92A81"},
            {"SKU-IC-003", "Galletas de vainilla", "Bakery", 2.50, 150, "cookie", "044D4593C92A81"},
            {"SKU-IC-004", "Yogurt de fresa 1 L", "Dairy", 6.50, 1050, "icecream", "04534593C92A81"},
            {"SKU-IC-005", "Atún en lata", "Seafood", 6.20, 170, "set_meal", "04654293C92A81"},
            {"SKU-IC-006", "Pan de molde", "Bakery", 8.50, 600, "bakery_dining", "04CA2AAE752681"},
            {"SKU-IC-007", "Gaseosa 500 ml", "Beverages", 3.00, 530, "local_drink", "04962BAE752681"},
            {"SKU-IC-008", "Azúcar rubia 1 kg", "Grocery", 4.20, 1000, "grain", "04952BAE752681"},
            {"SKU-IC-009", "Fideos spaghetti", "Grocery", 3.80, 500, "ramen_dining", "04F12EAE752681"},
            {"SKU-IC-010", "Chocolate de leche", "Grocery", 4.50, 100, "cake", "04EF2EAE752681"},
    };

    private final ObjectMapper mapper;
    private final PasswordHasher hasher;
    private final UserRepository users;
    private final StoreRepository stores;
    private final ProductRepository products;
    private final CartRepository carts;
    private final ShoppingSessionRepository sessions;
    private final AlertRepository alerts;
    private final CartEventRepository events;
    private final SessionStatRepository stats;

    public DataSeeder(ObjectMapper mapper, PasswordHasher hasher, UserRepository users, StoreRepository stores,
                      ProductRepository products, CartRepository carts, ShoppingSessionRepository sessions,
                      AlertRepository alerts, CartEventRepository events, SessionStatRepository stats) {
        this.mapper = mapper;
        this.hasher = hasher;
        this.users = users;
        this.stores = stores;
        this.products = products;
        this.carts = carts;
        this.sessions = sessions;
        this.alerts = alerts;
        this.events = events;
        this.stats = stats;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (users.count() > 0) return;
        log.info("Empty database: loading Innova Carty demo data");
        JsonNode db;
        try (InputStream in = new ClassPathResource("seed/db.json").getInputStream()) {
            db = mapper.readTree(in);
        }

        seedUsers();
        for (JsonNode n : db.path("stores")) {
            Store s = new Store();
            s.setName(n.path("name").asText());
            s.setFleetSize(n.path("fleetSize").asInt());
            s.setGatewayStatus(n.path("gatewayStatus").asText("ONLINE"));
            s.setAvgCheckoutSeconds(n.path("avgCheckoutSeconds").asInt());
            s.setCheckoutTimeVsCashier(n.path("checkoutTimeVsCashier").asInt());
            s.setShrinkagePreventedWeek(n.path("shrinkagePreventedWeek").asDouble());
            s.setDiscrepanciesSolvedWeek(n.path("discrepanciesSolvedWeek").asInt());
            s.setLastCatalogSyncAt(date(n, "lastCatalogSyncAt"));
            stores.save(s);
        }
        for (JsonNode n : db.path("products")) {
            products.save(product(n.path("id").asText(), n.path("name").asText(), n.path("category").asText(),
                    n.path("price").asDouble(), n.path("nominalWeight").asInt(), n.path("tolerance").asDouble(5),
                    n.path("icon").asText(), n.path("rfidPrefix").asText()));
        }
        for (Object[] p : PROTOTYPE_PRODUCTS) {
            products.save(product((String) p[0], (String) p[1], (String) p[2], (Double) p[3], (Integer) p[4], 8, (String) p[5], (String) p[6]));
        }

        for (JsonNode n : db.path("carts")) {
            Cart c = new Cart();
            c.setId(cartId(n.path("id").asText()));
            c.setStatus(CartStatus.valueOf(n.path("status").asText("AVAILABLE")));
            c.setZone(n.path("zone").asText());
            c.setItemsCount(n.path("itemsCount").asInt());
            c.setTotal(n.path("total").asDouble());
            c.setBudget(n.path("budget").asDouble());
            c.setLastEvent(n.path("lastEvent").asText());
            c.setLastEventAt(date(n, "lastEventAt"));
            c.setBattery(n.path("battery").asInt(100));
            c.setSessionId(n.path("sessionId").isNull() || n.path("sessionId").isMissingNode() ? null : n.path("sessionId").asText());
            c.setStoreId(1L);
            carts.save(c);
        }
        Cart prototype = new Cart();
        prototype.setId(PROTOTYPE_CART);
        prototype.setStatus(CartStatus.AVAILABLE);
        prototype.setZone("Entrance");
        prototype.setBattery(100);
        prototype.setLastEvent("Physical prototype (ESP32) ready");
        prototype.setLastEventAt(Instant.now());
        prototype.setStoreId(1L);
        carts.save(prototype);

        for (JsonNode n : db.path("shopping-sessions")) {
            ShoppingSession s = new ShoppingSession();
            s.setId(n.path("id").asText());
            s.setCartId(cartId(n.path("cartId").asText()));
            s.setShopper(n.path("shopper").asText());
            s.setStatus(SessionStatus.valueOf(n.path("status").asText("ACTIVE")));
            s.setBudget(n.path("budget").asDouble());
            s.setTotal(n.path("total").asDouble());
            s.setExpectedWeight(n.path("expectedWeight").asInt());
            s.setMeasuredWeight(n.path("measuredWeight").asInt());
            s.setWeightTolerance(n.path("weightTolerance").asInt());
            s.setStartedAt(date(n, "startedAt"));
            if (s.getStatus() == SessionStatus.PAID) s.setPaidAt(s.getStartedAt());
            for (JsonNode i : n.path("items")) {
                SessionItem item = new SessionItem();
                item.setSku(i.path("sku").asText());
                item.setName(i.path("name").asText());
                item.setRfidTag(i.path("rfidTag").asText());
                item.setQuantity(i.path("quantity").asInt(1));
                item.setUnitPrice(i.path("unitPrice").asDouble());
                item.setNominalWeight(i.path("nominalWeight").asInt());
                s.getItems().add(item);
            }
            sessions.save(s);
        }
        for (JsonNode n : db.path("alerts")) {
            Alert a = new Alert();
            a.setCartId(cartId(n.path("cartId").asText()));
            a.setType(AlertType.valueOf(n.path("type").asText("DEVICE")));
            a.setSeverity(AlertSeverity.valueOf(n.path("severity").asText("INFO")));
            a.setTitle(n.path("title").asText());
            a.setDescription(n.path("description").asText().replace(cartIdMention(), DEMO_CART_RENAMED));
            a.setZone(n.path("zone").asText());
            a.setCreatedAt(date(n, "createdAt"));
            a.setStatus(AlertStatus.valueOf(n.path("status").asText("OPEN")));
            alerts.save(a);
        }
        for (JsonNode n : db.path("cart-events")) {
            CartEvent e = new CartEvent();
            e.setCartId(cartId(n.path("cartId").asText()));
            e.setType(n.path("type").asText());
            e.setOccurredAt(date(n, "occurredAt"));
            e.setTitle(n.path("title").asText());
            e.setDetail(n.path("detail").asText());
            e.setTone(EventTone.valueOf(n.path("tone").asText("INFO")));
            events.save(e);
        }
        for (JsonNode n : db.path("session-stats")) {
            SessionStat s = new SessionStat();
            s.setHour(n.path("hour").asText());
            s.setSessions(n.path("sessions").asInt());
            stats.save(s);
        }
        log.info("Demo data loaded: {} carts, {} products", carts.count(), products.count());
    }

    private void seedUsers() {
        User admin = new User();
        admin.setEmail("lucia.medina@retailco.pe");
        admin.setPasswordHash(hasher.hash("demo1234"));
        admin.setFullName("Lucía Medina");
        admin.setRole(UserRole.ADMIN);
        admin.setJobTitle("Floor supervisor");
        admin.setStoreId(1L);
        admin.setSupervisorPin("2468");
        users.save(admin);

        User shopper = new User();
        shopper.setEmail("camila.rios@gmail.com");
        shopper.setPasswordHash(hasher.hash("demo1234"));
        shopper.setFullName("Camila Ríos");
        shopper.setRole(UserRole.SHOPPER);
        shopper.setJobTitle("Shopper");
        shopper.setStoreId(1L);
        users.save(shopper);
    }

    private static Product product(String id, String name, String category, double price, int grams, double tolerance, String icon, String tag) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setCategory(category);
        p.setPrice(price);
        p.setNominalWeight(grams);
        p.setTolerance(tolerance);
        p.setIcon(icon);
        p.setRfidPrefix(tag);
        p.setUpdatedAt(Instant.now());
        return p;
    }

    private static String cartId(String id) {
        return PROTOTYPE_CART.equals(id) ? DEMO_CART_RENAMED : id;
    }

    private static String cartIdMention() {
        return PROTOTYPE_CART;
    }

    private static Instant date(JsonNode node, String field) {
        String text = node.path(field).asText("");
        return text.isBlank() ? Instant.now() : Patch.parseInstant(text);
    }
}
