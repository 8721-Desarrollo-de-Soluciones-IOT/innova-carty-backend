package com.innovacarty.platform.catalog.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Supermarket that operates a fleet of smart carts. */
@Entity
@Table(name = "stores")
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int fleetSize;
    private String gatewayStatus;
    private int avgCheckoutSeconds;
    private int checkoutTimeVsCashier;
    private double shrinkagePreventedWeek;
    private int discrepanciesSolvedWeek;
    private Instant lastCatalogSyncAt;

    public Store() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFleetSize() {
        return fleetSize;
    }

    public void setFleetSize(int fleetSize) {
        this.fleetSize = fleetSize;
    }

    public String getGatewayStatus() {
        return gatewayStatus;
    }

    public void setGatewayStatus(String gatewayStatus) {
        this.gatewayStatus = gatewayStatus;
    }

    public int getAvgCheckoutSeconds() {
        return avgCheckoutSeconds;
    }

    public void setAvgCheckoutSeconds(int avgCheckoutSeconds) {
        this.avgCheckoutSeconds = avgCheckoutSeconds;
    }

    public int getCheckoutTimeVsCashier() {
        return checkoutTimeVsCashier;
    }

    public void setCheckoutTimeVsCashier(int checkoutTimeVsCashier) {
        this.checkoutTimeVsCashier = checkoutTimeVsCashier;
    }

    public double getShrinkagePreventedWeek() {
        return shrinkagePreventedWeek;
    }

    public void setShrinkagePreventedWeek(double shrinkagePreventedWeek) {
        this.shrinkagePreventedWeek = shrinkagePreventedWeek;
    }

    public int getDiscrepanciesSolvedWeek() {
        return discrepanciesSolvedWeek;
    }

    public void setDiscrepanciesSolvedWeek(int discrepanciesSolvedWeek) {
        this.discrepanciesSolvedWeek = discrepanciesSolvedWeek;
    }

    public Instant getLastCatalogSyncAt() {
        return lastCatalogSyncAt;
    }

    public void setLastCatalogSyncAt(Instant lastCatalogSyncAt) {
        this.lastCatalogSyncAt = lastCatalogSyncAt;
    }
}
