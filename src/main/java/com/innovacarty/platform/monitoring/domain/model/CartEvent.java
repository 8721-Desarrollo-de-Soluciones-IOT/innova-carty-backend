package com.innovacarty.platform.monitoring.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Timeline entry of a cart (item added, payment, lock, unlock...). */
@Entity
@Table(name = "cart_events")
public class CartEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String cartId;
    private String type;
    private Instant occurredAt;
    private String title;
    @Column(length = 600)
    private String detail;
    @Enumerated(EnumType.STRING)
    private EventTone tone;

    public CartEvent() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public EventTone getTone() {
        return tone;
    }

    public void setTone(EventTone tone) {
        this.tone = tone;
    }
}
