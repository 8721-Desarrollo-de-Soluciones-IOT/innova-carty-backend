package com.innovacarty.platform.monitoring.interfaces.rest;

import com.innovacarty.platform.monitoring.domain.model.AuditLog;
import com.innovacarty.platform.monitoring.infrastructure.persistence.AuditLogRepository;
import com.innovacarty.platform.shared.infrastructure.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit logs", description = "Supervisor actions (unlocks, audit flags)")
@Transactional
public class AuditLogsController {
    private final AuditLogRepository auditLogs;

    public AuditLogsController(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AuditLog> getAll(@RequestParam(required = false) String cartId) {
        List<AuditLog> list = cartId == null || cartId.isBlank() ? auditLogs.findAll() : auditLogs.findByCartId(cartId);
        return list.stream()
                .sorted(Comparator.comparing(AuditLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditLog create(@RequestBody AuditLog log, @RequestAttribute(CurrentUser.ATTRIBUTE) CurrentUser user) {
        log.setId(null);
        log.setUserId(user.userId());
        if (log.getCreatedAt() == null) log.setCreatedAt(Instant.now());
        return auditLogs.save(log);
    }
}
