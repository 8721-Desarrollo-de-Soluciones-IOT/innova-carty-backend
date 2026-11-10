package com.innovacarty.platform.monitoring.domain.model;

/** Live state of a cart. */
public enum CartStatus {
    AVAILABLE,
    IN_SESSION,
    DISCREPANCY,
    LOCKED,
    PAID,
    OFFLINE
}
