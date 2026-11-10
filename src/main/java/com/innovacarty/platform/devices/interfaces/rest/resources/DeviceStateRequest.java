package com.innovacarty.platform.devices.interfaces.rest.resources;

import java.util.List;

/**
 * State reported by the cart.
 *
 * @param items          tags in the cart with their quantity
 * @param measuredWeight stable weight in grams, or -1 / null when the load cell is not connected
 * @param battery        battery percentage (optional)
 * @param zone           zone of the store where the cart is (optional)
 */
public record DeviceStateRequest(List<Item> items, Integer measuredWeight, Integer battery, String zone) {

    public record Item(String tag, Integer quantity) {
    }
}
