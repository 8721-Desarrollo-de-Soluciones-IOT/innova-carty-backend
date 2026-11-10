package com.innovacarty.platform.monitoring.infrastructure.persistence;

import com.innovacarty.platform.monitoring.domain.model.CartEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartEventRepository extends JpaRepository<CartEvent, Long> {
    List<CartEvent> findByCartId(String cartId);
}
