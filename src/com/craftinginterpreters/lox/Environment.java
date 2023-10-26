package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Environment {
    final Environment enclosing;
    private final List<Object> values = new ArrayList<>();

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    void define(Object value) {
        values.add(value);
    }

    Object getAt(int distance, int slot) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
            assert environment != null : "Enclosing scope is null";
        }
        return environment.values.get(slot);
    }

    void assignAt(int distance, int slot, Object value) {
        var environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
            assert environment != null : "Enclosing scope is null";
        }
        environment.values.set(slot, value);
    }

    @Override
    public String toString() {
        final var builder = new StringBuilder();
        builder.append(enclosing == null ? "{Environment}" : enclosing.toString());
        builder.append("\n -> ");
        builder.append("[");
        if (!values.isEmpty()) {
            builder.append(values.get(0));
        }
        for (int i = 1; i < values.size(); i++) {
            builder.append(", ").append(values.get(i));
        }
        builder.append("]");
        return builder.toString();
    }
}
