package com.innovacarty.platform.monitoring.infrastructure.persistence;

import com.innovacarty.platform.monitoring.domain.model.Alert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByCartId(String cartId);
}
