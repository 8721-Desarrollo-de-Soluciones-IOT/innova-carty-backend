package com.innovacarty.platform.monitoring.interfaces.rest;

import static com.innovacarty.platform.shared.interfaces.rest.Errors.notFound;

import com.innovacarty.platform.monitoring.domain.model.SessionStatus;
import com.innovacarty.platform.monitoring.domain.model.ShoppingSession;
import com.innovacarty.platform.monitoring.infrastructure.persistence.ShoppingSessionRepository;
import com.innovacarty.platform.shared.interfaces.rest.Patch;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shopping-sessions")
@Tag(name = "Shopping sessions", description = "Orders of each cart with their items and weights")
@Transactional
public class ShoppingSessionsController {
    private final ShoppingSessionRepository sessions;

    public ShoppingSessionsController(ShoppingSessionRepository sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ShoppingSession> getAll(@RequestParam(required = false) String cartId) {
        if (cartId != null && !cartId.isBlank()) return sessions.findByCartIdOrderByStartedAtDesc(cartId);
        return sessions.findAll().stream()
                .sorted(Comparator.comparing(ShoppingSession::getStartedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ShoppingSession getById(@PathVariable String id) {
        return sessions.findById(id).orElseThrow(() -> notFound("Session " + id + " not found"));
    }

    @PatchMapping("/{id}")
    public ShoppingSession update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        ShoppingSession session = getById(id);
        Patch patch = new Patch(body);
        patch.integer("measuredWeight", session::setMeasuredWeight);
        patch.number("budget", session::setBudget);
        patch.enumeration("status", SessionStatus.class, session::setStatus);
        patch.string("shopper", session::setShopper);
        return session;
    }
}
