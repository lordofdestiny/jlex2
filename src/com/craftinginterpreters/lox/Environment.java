package com.craftinginterpreters.lox;

import java.util.ArrayList;
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
            values.forEach(System.out::println);
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
}
