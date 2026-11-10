package com.innovacarty.platform.monitoring.interfaces.rest;

import static com.innovacarty.platform.shared.interfaces.rest.Errors.badRequest;
import static com.innovacarty.platform.shared.interfaces.rest.Errors.conflict;

import com.innovacarty.platform.iam.application.AuthService;
import com.innovacarty.platform.monitoring.application.CartLifecycleService;
import com.innovacarty.platform.monitoring.domain.model.Cart;
import com.innovacarty.platform.monitoring.domain.model.CartStatus;
import com.innovacarty.platform.monitoring.infrastructure.persistence.CartRepository;
import com.innovacarty.platform.shared.infrastructure.security.CurrentUser;
import com.innovacarty.platform.shared.interfaces.rest.Patch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/carts")
@Tag(name = "Carts", description = "Fleet of smart carts (Web Console and admin view of the app)")
@Transactional
public class CartsController {
    private final CartRepository carts;
    private final CartLifecycleService lifecycle;
    private final AuthService auth;

    public CartsController(CartRepository carts, CartLifecycleService lifecycle, AuthService auth) {
        this.carts = carts;
        this.lifecycle = lifecycle;
        this.auth = auth;
    }

    public record UnlockRequest(String pin, String resolution, String note) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Cart> getAll(@RequestParam(required = false) String status) {
        List<Cart> all = carts.findAllByOrderByIdAsc();
        if (status == null || status.isBlank()) return all;
        return all.stream().filter(c -> c.getStatus().name().equalsIgnoreCase(status)).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Cart getById(@PathVariable String id) {
        return lifecycle.getCart(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a cart of the fleet")
    public Cart create(@RequestBody Cart cart) {
        if (cart.getId() == null || cart.getId().isBlank()) throw badRequest("The cart needs an id, e.g. CART-0428");
        cart.setId(CartLifecycleService.normalizeCartId(cart.getId()));
        if (carts.existsById(cart.getId())) throw conflict(cart.getId() + " already exists");
        if (cart.getStatus() == null) cart.setStatus(CartStatus.AVAILABLE);
        if (cart.getLastEventAt() == null) cart.setLastEventAt(Instant.now());
        if (cart.getStoreId() == null) cart.setStoreId(1L);
        return carts.save(cart);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partial update (status, zone, lastEvent, budget...)")
    public Cart update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Cart cart = lifecycle.getCart(id);
        Patch patch = new Patch(body);
        patch.enumeration("status", CartStatus.class, cart::setStatus);
        patch.string("zone", cart::setZone);
        patch.string("lastEvent", cart::setLastEvent);
        patch.instant("lastEventAt", cart::setLastEventAt);
        patch.integer("battery", cart::setBattery);
        patch.integer("itemsCount", cart::setItemsCount);
        patch.number("total", cart::setTotal);
        patch.number("budget", cart::setBudget);
        patch.string("sessionId", cart::setSessionId);
        return cart;
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Unlock a cart with the supervisor PIN (resolves its alerts and writes the audit log)")
    public Cart unlock(@PathVariable String id, @RequestBody UnlockRequest request,
                       @RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        auth.verifySupervisorPin(user.userId(), request.pin());
        Cart cart = lifecycle.getCart(id);
        if (cart.getStatus() != CartStatus.LOCKED && cart.getStatus() != CartStatus.DISCREPANCY) {
            throw conflict(cart.getId() + " is not locked");
        }
        String resolution = request.resolution() == null || request.resolution().isBlank() ? "Checked by supervisor" : request.resolution();
        return lifecycle.unlock(cart, resolution, request.note(), user.userId(), user.name());
    }

    @PostMapping("/{id}/finish")
    @Operation(summary = "Close the shopping session of a cart and make it available again")
    public Cart finish(@PathVariable String id) {
        Cart cart = lifecycle.getCart(id);
        lifecycle.finish(cart);
        return cart;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        carts.delete(lifecycle.getCart(id));
        return ResponseEntity.noContent().build();
    }
}
