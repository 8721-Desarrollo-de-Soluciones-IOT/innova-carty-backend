package com.innovacarty.platform.monitoring.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One shopping trip with a cart, from the first item to the exit. */
@Entity
@Table(name = "shopping_sessions")
public class ShoppingSession {
    @Id
    private String id;
    private String cartId;
    private String shopper;
    private Long shopperUserId;
    @Enumerated(EnumType.STRING)
    private SessionStatus status;
    private double budget;
    private double total;
    private int expectedWeight;
    private int measuredWeight;
    private int weightTolerance;
    private Instant startedAt;
    private Instant paidAt;
    private Instant closedAt;
    private String paymentMethod;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "session_items", joinColumns = @JoinColumn(name = "session_id"))
    @OrderColumn(name = "line_no")
    private List<SessionItem> items = new ArrayList<>();

    public ShoppingSession() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public String getShopper() {
        return shopper;
    }

    public void setShopper(String shopper) {
        this.shopper = shopper;
    }

    public Long getShopperUserId() {
        return shopperUserId;
    }

    public void setShopperUserId(Long shopperUserId) {
        this.shopperUserId = shopperUserId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public double getBudget() {
        return budget;
    }

    public void setBudget(double budget) {
        this.budget = budget;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public int getExpectedWeight() {
        return expectedWeight;
    }

    public void setExpectedWeight(int expectedWeight) {
        this.expectedWeight = expectedWeight;
    }

    public int getMeasuredWeight() {
        return measuredWeight;
    }

    public void setMeasuredWeight(int measuredWeight) {
        this.measuredWeight = measuredWeight;
    }

    public int getWeightTolerance() {
        return weightTolerance;
    }

    public void setWeightTolerance(int weightTolerance) {
        this.weightTolerance = weightTolerance;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public List<SessionItem> getItems() {
        return items;
    }

    public void setItems(List<SessionItem> items) {
        this.items = items;
    }
}
