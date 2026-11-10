package com.innovacarty.platform.monitoring.interfaces.rest;

import static com.innovacarty.platform.shared.interfaces.rest.Errors.notFound;

import com.innovacarty.platform.monitoring.domain.model.Alert;
import com.innovacarty.platform.monitoring.domain.model.AlertStatus;
import com.innovacarty.platform.monitoring.infrastructure.persistence.AlertRepository;
import com.innovacarty.platform.shared.interfaces.rest.Patch;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "Geofence, weight, RFID and device alerts")
@Transactional
public class AlertsController {
    private final AlertRepository alerts;

    public AlertsController(AlertRepository alerts) {
        this.alerts = alerts;
    }

    /** Same query params as the fake API: cartId, status, _sort=createdAt, _order=asc|desc. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<Alert> getAll(@RequestParam(required = false) String cartId,
                              @RequestParam(required = false) String status,
                              @RequestParam(name = "_order", required = false) String order) {
        List<Alert> list = cartId == null || cartId.isBlank() ? alerts.findAll() : alerts.findByCartId(cartId);
        Comparator<Alert> byDate = Comparator.comparing(Alert::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
        return list.stream()
                .filter(a -> status == null || status.isBlank() || a.getStatus().name().equalsIgnoreCase(status))
                .sorted("asc".equalsIgnoreCase(order) ? byDate : byDate.reversed())
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Alert getById(@PathVariable Long id) {
        return alerts.findById(id).orElseThrow(() -> notFound("Alert " + id + " not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Alert create(@RequestBody Alert alert) {
        alert.setId(null);
        if (alert.getCreatedAt() == null) alert.setCreatedAt(Instant.now());
        if (alert.getStatus() == null) alert.setStatus(AlertStatus.OPEN);
        return alerts.save(alert);
    }

    @PatchMapping("/{id}")
    public Alert update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Alert alert = getById(id);
        new Patch(body).enumeration("status", AlertStatus.class, alert::setStatus);
        return alert;
    }
}
