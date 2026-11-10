package com.innovacarty.platform.catalog.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Catalog product. rfidPrefix holds the tag UID (or UID prefix) that identifies it. */
@Entity
@Table(name = "products")
public class Product {
    @Id
    private String id;
    private String name;
    private String category;
    private double price;
    private int nominalWeight;
    private double tolerance;
    private String rfidPrefix;
    private String icon;
    private Instant updatedAt;

    public Product() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getNominalWeight() {
        return nominalWeight;
    }

    public void setNominalWeight(int nominalWeight) {
        this.nominalWeight = nominalWeight;
    }

    public double getTolerance() {
        return tolerance;
    }

    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    public String getRfidPrefix() {
        return rfidPrefix;
    }

    public void setRfidPrefix(String rfidPrefix) {
        this.rfidPrefix = rfidPrefix;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
