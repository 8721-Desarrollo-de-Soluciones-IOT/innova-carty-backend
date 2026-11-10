package com.innovacarty.platform.catalog.interfaces.rest;

import static com.innovacarty.platform.shared.interfaces.rest.Errors.badRequest;
import static com.innovacarty.platform.shared.interfaces.rest.Errors.conflict;
import static com.innovacarty.platform.shared.interfaces.rest.Errors.notFound;

import com.innovacarty.platform.catalog.domain.model.Product;
import com.innovacarty.platform.catalog.infrastructure.persistence.ProductRepository;
import com.innovacarty.platform.shared.interfaces.rest.Patch;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Catalog with prices, nominal weights, tolerances and RFID tags")
@Transactional
public class ProductsController {
    private final ProductRepository products;

    public ProductsController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Product> getAll() {
        return products.findAll().stream().sorted(Comparator.comparing(Product::getId)).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Product getById(@PathVariable String id) {
        return products.findById(id).orElseThrow(() -> notFound("Product " + id + " not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@RequestBody Product product) {
        if (product.getId() == null || product.getId().isBlank()) throw badRequest("The product needs a SKU id");
        if (products.existsById(product.getId())) throw conflict(product.getId() + " already exists");
        product.setUpdatedAt(Instant.now());
        return products.save(product);
    }

    @PatchMapping("/{id}")
    public Product update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Product product = getById(id);
        Patch patch = new Patch(body);
        patch.string("name", product::setName);
        patch.string("category", product::setCategory);
        patch.number("price", product::setPrice);
        patch.integer("nominalWeight", product::setNominalWeight);
        patch.number("tolerance", product::setTolerance);
        patch.string("rfidPrefix", product::setRfidPrefix);
        patch.string("icon", product::setIcon);
        product.setUpdatedAt(Instant.now());
        return product;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        products.delete(getById(id));
        return ResponseEntity.noContent().build();
    }
}
