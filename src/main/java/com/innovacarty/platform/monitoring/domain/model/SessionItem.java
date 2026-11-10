package com.innovacarty.platform.monitoring.domain.model;

import jakarta.persistence.*;

/** Product line of a shopping session. */
@Embeddable
public class SessionItem {
    private String sku;
    private String name;
    private String rfidTag;
    private int quantity;
    private double unitPrice;
    private int nominalWeight;

    public SessionItem() {
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRfidTag() {
        return rfidTag;
    }

    public void setRfidTag(String rfidTag) {
        this.rfidTag = rfidTag;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public int getNominalWeight() {
        return nominalWeight;
    }

    public void setNominalWeight(int nominalWeight) {
        this.nominalWeight = nominalWeight;
    }
}
