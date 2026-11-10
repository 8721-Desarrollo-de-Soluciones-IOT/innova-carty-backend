package com.innovacarty.platform.devices.application;

import com.innovacarty.platform.devices.interfaces.rest.resources.DeviceCartResource;
import com.innovacarty.platform.devices.interfaces.rest.resources.DeviceEventRequest;
import com.innovacarty.platform.devices.interfaces.rest.resources.DeviceStateRequest;
import com.innovacarty.platform.monitoring.application.CartLifecycleService;
import com.innovacarty.platform.monitoring.domain.model.*;
import com.innovacarty.platform.payment.application.PaymentService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Receives what the ESP32 of each cart reports and answers with the state it must show. */
@Service
@Transactional
public class DeviceGatewayService {
    private final CartLifecycleService lifecycle;
    private final PaymentService payments;

    public DeviceGatewayService(CartLifecycleService lifecycle, PaymentService payments) {
        this.lifecycle = lifecycle;
        this.payments = payments;
    }

    /** Polled by the cart every ~2 s: tells it if the app paid or the console unlocked it. */
    public DeviceCartResource heartbeat(String cartId) {
        Cart cart = lifecycle.findOrRegisterCart(cartId);
        cart.setLastSeenAt(Instant.now());
        if (cart.getStatus() == CartStatus.OFFLINE) {
            cart.setStatus(cart.getSessionId() == null ? CartStatus.AVAILABLE : CartStatus.IN_SESSION);
        }
        return DeviceCartResource.of(cart, lifecycle.openSession(cart).orElse(null));
    }

    /** Items read by RFID (tag UID + quantity), stable weight, battery and zone. */
    public DeviceCartResource syncState(String cartId, DeviceStateRequest request) {
        Cart cart = lifecycle.findOrRegisterCart(cartId);
        cart.setLastSeenAt(Instant.now());
        if (request.battery() != null) cart.setBattery(request.battery());
        if (request.zone() != null && !request.zone().isBlank()) cart.setZone(request.zone());
        if (cart.getStatus() == CartStatus.OFFLINE) {
            cart.setStatus(cart.getSessionId() == null ? CartStatus.AVAILABLE : CartStatus.IN_SESSION);
        }

        Map<String, Integer> tags = new LinkedHashMap<>();
        if (request.items() != null) {
            request.items().forEach(i -> {
                if (i != null && i.tag() != null) tags.merge(i.tag(), i.quantity() == null ? 1 : i.quantity(), Integer::sum);
            });
        }
        boolean hasItems = tags.values().stream().anyMatch(q -> q > 0);

        ShoppingSession session = lifecycle.openSession(cart).orElse(null);
        if (session == null && hasItems) session = lifecycle.startSession(cart);
        if (session != null && session.getStatus() == SessionStatus.ACTIVE) {
            lifecycle.syncItems(cart, session, tags);
            if (request.measuredWeight() != null && request.measuredWeight() >= 0) {
                lifecycle.checkWeight(cart, session, request.measuredWeight());
            }
        }
        return DeviceCartResource.of(cart, session);
    }

    /** Events of the cart: GEOFENCE, HELP, CHECKOUT, CARD_PAYMENT, LOCAL_UNLOCK, UNKNOWN_TAG, EXIT, BOOT. */
    public DeviceCartResource handleEvent(String cartId, DeviceEventRequest request) {
        Cart cart = lifecycle.findOrRegisterCart(cartId);
        cart.setLastSeenAt(Instant.now());
        if (request.zone() != null && !request.zone().isBlank()) cart.setZone(request.zone());
        String type = request.type() == null ? "" : request.type().trim().toUpperCase(Locale.ROOT);
        String detail = request.detail() == null ? "" : request.detail();

        switch (type) {
            case "BOOT" -> {
                if (cart.getStatus() == CartStatus.OFFLINE) cart.setStatus(CartStatus.AVAILABLE);
                // The cart restarted with an empty basket: a paid order cannot continue.
                if (cart.getStatus() == CartStatus.PAID) lifecycle.finish(cart);
                lifecycle.touch(cart, "Cart online");
                lifecycle.record(cart.getId(), "DEVICE", "Cart online", detail, EventTone.INFO);
            }
            case "GEOFENCE" -> lifecycle.lockAtExit(cart);
            case "HELP" -> {
                lifecycle.touch(cart, "Help requested");
                lifecycle.raise(cart, AlertType.DEVICE, AlertSeverity.WARNING, "Shopper asked for help",
                        cart.getId() + " asked for assistance at " + cart.getZone() + ".");
                lifecycle.record(cart.getId(), "HELP", "Help requested", detail, EventTone.WARNING);
            }
            case "CHECKOUT" -> {
                lifecycle.touch(cart, "Checkout QR on screen");
                lifecycle.record(cart.getId(), "CHECKOUT", "Checkout started", "Payment QR shown on the cart screen", EventTone.INFO);
            }
            case "CHECKOUT_CANCELLED" -> lifecycle.record(cart.getId(), "CHECKOUT", "Checkout cancelled", detail, EventTone.INFO);
            case "CARD_PAYMENT" -> payments.pay(cart.getId(), "CARD_RFID", null, false);
            case "LOCAL_UNLOCK" -> lifecycle.unlock(cart, "Unlocked with the supervisor key fob", detail, 0L, "key fob");
            case "UNKNOWN_TAG" -> {
                lifecycle.raise(cart, AlertType.RFID, AlertSeverity.INFO, "Unknown RFID tag",
                        "Tag " + detail + " read by " + cart.getId() + " is not in the catalog.");
                lifecycle.record(cart.getId(), "RFID", "Unknown tag " + detail, "Not in the catalog", EventTone.WARNING);
            }
            case "EXIT" -> lifecycle.finish(cart);
            default -> lifecycle.record(cart.getId(), type.isEmpty() ? "DEVICE" : type, type, detail, EventTone.INFO);
        }
        return DeviceCartResource.of(cart, lifecycle.openSession(cart).orElse(null));
    }
}
