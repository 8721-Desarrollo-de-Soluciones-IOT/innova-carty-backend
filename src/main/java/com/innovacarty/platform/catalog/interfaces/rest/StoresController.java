package com.innovacarty.platform.catalog.interfaces.rest;

import static com.innovacarty.platform.shared.interfaces.rest.Errors.notFound;

import com.innovacarty.platform.catalog.domain.model.Store;
import com.innovacarty.platform.catalog.infrastructure.persistence.StoreRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores")
@Tag(name = "Stores", description = "Supermarkets and their KPIs")
public class StoresController {
    private final StoreRepository stores;

    public StoresController(StoreRepository stores) {
        this.stores = stores;
    }

    @GetMapping
    public List<Store> getAll() {
        return stores.findAll();
    }

    @GetMapping("/{id}")
    public Store getById(@PathVariable Long id) {
        return stores.findById(id).orElseThrow(() -> notFound("Store " + id + " not found"));
    }
}
