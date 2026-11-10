package com.innovacarty.platform.monitoring.interfaces.rest;

import com.innovacarty.platform.monitoring.domain.model.SessionStat;
import com.innovacarty.platform.monitoring.infrastructure.persistence.SessionStatRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/session-stats")
@Tag(name = "Session stats", description = "Shopping sessions per hour (dashboard chart)")
public class SessionStatsController {
    private final SessionStatRepository stats;

    public SessionStatsController(SessionStatRepository stats) {
        this.stats = stats;
    }

    @GetMapping
    public List<SessionStat> getAll() {
        return stats.findAllByOrderByHourAsc();
    }
}
