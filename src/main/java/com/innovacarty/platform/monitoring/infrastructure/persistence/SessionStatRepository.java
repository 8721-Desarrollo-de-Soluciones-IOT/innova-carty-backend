package com.innovacarty.platform.monitoring.infrastructure.persistence;

import com.innovacarty.platform.monitoring.domain.model.SessionStat;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionStatRepository extends JpaRepository<SessionStat, Long> {
    Optional<SessionStat> findByHour(String hour);
    List<SessionStat> findAllByOrderByHourAsc();
}
