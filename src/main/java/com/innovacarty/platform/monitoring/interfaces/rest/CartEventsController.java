package com.innovacarty.platform.monitoring.interfaces.rest;

import com.innovacarty.platform.monitoring.domain.model.CartEvent;
import com.innovacarty.platform.monitoring.domain.model.EventTone;
import com.innovacarty.platform.monitoring.infrastructure.persistence.CartEventRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart-events")
@Tag(name = "Cart events", description = "Timeline of each cart")
@Transactional
public class CartEventsController {
    private final CartEventRepository events;

    public CartEventsController(CartEventRepository events) {
        this.events = events;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CartEvent> getAll(@RequestParam(required = false) String cartId,
                                  @RequestParam(name = "_order", required = false) String order,
                                  @RequestParam(name = "_limit", required = false) Integer limit) {
        List<CartEvent> list = cartId == null || cartId.isBlank() ? events.findAll() : events.findByCartId(cartId);
        Comparator<CartEvent> byDate = Comparator.comparing(CartEvent::getOccurredAt, Comparator.nullsFirst(Comparator.naturalOrder()));
        return list.stream()
                .sorted("asc".equalsIgnoreCase(order) ? byDate : byDate.reversed())
                .limit(limit == null || limit <= 0 ? Long.MAX_VALUE : limit)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartEvent create(@RequestBody CartEvent event) {
        event.setId(null);
        if (event.getOccurredAt() == null) event.setOccurredAt(Instant.now());
        if (event.getTone() == null) event.setTone(EventTone.INFO);
        return events.save(event);
    }
}
