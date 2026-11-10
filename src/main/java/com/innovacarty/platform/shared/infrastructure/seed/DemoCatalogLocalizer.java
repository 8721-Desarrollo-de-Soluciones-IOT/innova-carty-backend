package com.innovacarty.platform.shared.infrastructure.seed;

import com.innovacarty.platform.catalog.domain.model.Product;
import com.innovacarty.platform.catalog.infrastructure.persistence.ProductRepository;
import com.innovacarty.platform.monitoring.domain.model.CartEvent;
import com.innovacarty.platform.monitoring.domain.model.SessionItem;
import com.innovacarty.platform.monitoring.domain.model.ShoppingSession;
import com.innovacarty.platform.monitoring.infrastructure.persistence.CartEventRepository;
import com.innovacarty.platform.monitoring.infrastructure.persistence.ShoppingSessionRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The store is in Peru, so the demo products of Sprint 1 get their Spanish names (products,
 * items of saved orders and event titles). It only renames the English demo names, so it is
 * safe to run on every start and it never touches products created by the store.
 */
@Component
@Order(2)
public class DemoCatalogLocalizer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoCatalogLocalizer.class);

    static final Map<String, String> SPANISH_NAMES = Map.ofEntries(
            Map.entry("Whole milk 1 L", "Leche entera 1 L"),
            Map.entry("Long-grain rice 5 kg", "Arroz extra 5 kg"),
            Map.entry("Free-range eggs x15", "Huevos de corral x15"),
            Map.entry("Extra virgin olive oil 500 ml", "Aceite de oliva extra virgen 500 ml"),
            Map.entry("Whole wheat bread 600 g", "Pan integral 600 g"),
            Map.entry("Greek yogurt 1 kg", "Yogurt griego 1 kg"),
            Map.entry("Chicken breast 1 kg", "Pechuga de pollo 1 kg"),
            Map.entry("Ground coffee 250 g", "Café molido 250 g"),
            Map.entry("Salmon fillet 500 g", "Filete de salmón 500 g"),
            Map.entry("Bananas 1 kg", "Plátanos 1 kg"),
            Map.entry("Avocado Hass 1 kg", "Palta Hass 1 kg"),
            Map.entry("Sparkling water 2 L", "Agua con gas 2 L"),
            Map.entry("Orange juice 1 L", "Jugo de naranja 1 L"),
            Map.entry("Laundry detergent 2 kg", "Detergente 2 kg"),
            Map.entry("Toilet paper x12", "Papel higiénico x12"));

    private final ProductRepository products;
    private final ShoppingSessionRepository sessions;
    private final CartEventRepository events;

    public DemoCatalogLocalizer(ProductRepository products, ShoppingSessionRepository sessions, CartEventRepository events) {
        this.products = products;
        this.sessions = sessions;
        this.events = events;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int renamed = 0;
        for (Product product : products.findAll()) {
            String spanish = SPANISH_NAMES.get(product.getName());
            if (spanish != null) {
                product.setName(spanish);
                renamed++;
            }
        }
        for (ShoppingSession session : sessions.findAll()) {
            for (SessionItem item : session.getItems()) {
                String spanish = SPANISH_NAMES.get(item.getName());
                if (spanish != null) item.setName(spanish);
            }
        }
        for (CartEvent event : events.findAll()) {
            String title = event.getTitle();
            if (title == null) continue;
            for (Map.Entry<String, String> name : SPANISH_NAMES.entrySet()) {
                if (title.contains(name.getKey())) {
                    event.setTitle(title.replace(name.getKey(), name.getValue()));
                    break;
                }
            }
        }
        if (renamed > 0) log.info("Demo catalog in Spanish: {} products renamed", renamed);
    }
}
