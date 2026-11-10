package com.innovacarty.platform.devices.interfaces.rest;

import com.innovacarty.platform.devices.application.DeviceGatewayService;
import com.innovacarty.platform.devices.interfaces.rest.resources.DeviceCartResource;
import com.innovacarty.platform.devices.interfaces.rest.resources.DeviceEventRequest;
import com.innovacarty.platform.devices.interfaces.rest.resources.DeviceStateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** Endpoints for the ESP32 of the carts (header X-Device-Key). */
@RestController
@RequestMapping("/api/v1/devices/carts/{cartId}")
@Tag(name = "Devices", description = "Smart cart (ESP32) gateway")
public class DeviceCartsController {
    private final DeviceGatewayService gateway;

    public DeviceCartsController(DeviceGatewayService gateway) {
        this.gateway = gateway;
    }

    @GetMapping
    @Operation(summary = "Heartbeat: current status of the cart (PAID after the app pays, IN_SESSION after an unlock)")
    public DeviceCartResource heartbeat(@PathVariable String cartId) {
        return gateway.heartbeat(cartId);
    }

    @PutMapping("/state")
    @Operation(summary = "Report the tags in the cart, the stable weight, battery and zone")
    public DeviceCartResource syncState(@PathVariable String cartId, @RequestBody DeviceStateRequest request) {
        return gateway.syncState(cartId, request);
    }

    @PostMapping("/events")
    @Operation(summary = "Report an event: GEOFENCE, HELP, CHECKOUT, CARD_PAYMENT, LOCAL_UNLOCK, UNKNOWN_TAG, EXIT, BOOT")
    public DeviceCartResource event(@PathVariable String cartId, @RequestBody DeviceEventRequest request) {
        return gateway.handleEvent(cartId, request);
    }
}
