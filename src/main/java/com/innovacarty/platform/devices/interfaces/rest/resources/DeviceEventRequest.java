package com.innovacarty.platform.devices.interfaces.rest.resources;

/**
 * Event reported by the cart.
 *
 * @param type   BOOT, GEOFENCE, HELP, CHECKOUT, CHECKOUT_CANCELLED, CARD_PAYMENT, LOCAL_UNLOCK, UNKNOWN_TAG or EXIT
 * @param detail free text (tag UID, door name...)
 * @param zone   zone where it happened, e.g. "Exit 1" (optional)
 */
public record DeviceEventRequest(String type, String detail, String zone) {
}
