package com.innovacarty.platform.devices.interfaces.rest.resources;

import com.innovacarty.platform.monitoring.domain.model.Cart;
import com.innovacarty.platform.monitoring.domain.model.ShoppingSession;

/** Small answer for the ESP32: what the cart must show and do. */
public record DeviceCartResource(
        String cartId,
        String status,
        String sessionId,
        String shopper,
        int itemsCount,
        double total,
        double budget,
        boolean paid,
        boolean locked) {

    public static DeviceCartResource of(Cart cart, ShoppingSession session) {
        return new DeviceCartResource(
                cart.getId(),
                cart.getStatus().name(),
                cart.getSessionId(),
                session == null ? null : session.getShopper(),
                cart.getItemsCount(),
                cart.getTotal(),
                cart.getBudget(),
                cart.getStatus().name().equals("PAID"),
                cart.getStatus().name().equals("LOCKED"));
    }
}
