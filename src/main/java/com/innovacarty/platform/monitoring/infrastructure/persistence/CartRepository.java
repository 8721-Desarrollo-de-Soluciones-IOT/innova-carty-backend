package com.innovacarty.platform.monitoring.infrastructure.persistence;

import com.innovacarty.platform.monitoring.domain.model.Cart;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartRepository extends JpaRepository<Cart, String> {
    List<Cart> findAllByOrderByIdAsc();
}
