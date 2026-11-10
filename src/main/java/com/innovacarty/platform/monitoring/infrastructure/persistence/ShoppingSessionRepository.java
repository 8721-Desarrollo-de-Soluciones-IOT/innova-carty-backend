package com.innovacarty.platform.monitoring.infrastructure.persistence;

import com.innovacarty.platform.monitoring.domain.model.ShoppingSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShoppingSessionRepository extends JpaRepository<ShoppingSession, String> {
    Optional<ShoppingSession> findFirstByShopperUserIdAndClosedAtIsNullOrderByStartedAtDesc(Long shopperUserId);
    List<ShoppingSession> findByCartIdOrderByStartedAtDesc(String cartId);
    List<ShoppingSession> findByShopperUserIdOrderByStartedAtDesc(Long shopperUserId);
}
