package com.innovacarty.platform.shared.interfaces.rest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Consumer;

/** Reads the fields of a PATCH body (same partial updates the json-server fake API accepted). */
public record Patch(Map<String, Object> body) {

    public void string(String field, Consumer<String> setter) {
        if (body.containsKey(field)) setter.accept(body.get(field) == null ? null : String.valueOf(body.get(field)));
    }

    public void number(String field, Consumer<Double> setter) {
        Object value = body.get(field);
        if (value instanceof Number n) setter.accept(n.doubleValue());
        else if (value instanceof String s && !s.isBlank()) setter.accept(Double.parseDouble(s));
    }

    public void integer(String field, Consumer<Integer> setter) {
        number(field, v -> setter.accept((int) Math.round(v)));
    }

    public void instant(String field, Consumer<Instant> setter) {
        Object value = body.get(field);
        if (value instanceof String s && !s.isBlank()) setter.accept(parseInstant(s));
    }

    public <E extends Enum<E>> void enumeration(String field, Class<E> type, Consumer<E> setter) {
        Object value = body.get(field);
        if (value instanceof String s && !s.isBlank()) {
            try {
                setter.accept(Enum.valueOf(type, s.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw Errors.badRequest("Invalid value '" + s + "' for " + field);
            }
        }
    }

    /** Accepts both "2026-10-03T10:42:18-05:00" and "2026-10-03T15:42:18Z". */
    public static Instant parseInstant(String text) {
        return OffsetDateTime.parse(text).toInstant();
    }
}
