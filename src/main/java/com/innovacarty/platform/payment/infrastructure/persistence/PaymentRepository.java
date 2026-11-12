package com.innovacarty.platform.payment.infrastructure.persistence;

import com.innovacarty.platform.payment.domain.model.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByUserIdOrderByPaidAtDesc(Long userId);
    List<Payment> findByCartIdOrderByPaidAtDesc(String cartId);
    List<Payment> findAllByOrderByPaidAtDesc();
    Optional<Payment> findByExternalEventId(String externalEventId);
}
