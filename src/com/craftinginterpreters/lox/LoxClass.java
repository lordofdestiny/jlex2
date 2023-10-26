package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class LoxClass implements LoxCallable {
    final String name;
    private final Map<String, LoxFunction> methods;

    LoxClass(String name, Map<String, LoxFunction> methods) {
        this.name = name;
        this.methods = methods;
    }

    Optional<LoxFunction> findMethod(String name) {
        return Optional.ofNullable(methods.getOrDefault(name, null));
    }

    @Override
    public int arity() {
        return 0;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        final var instance = new LoxInstance(this);
        return instance;
    }

    @Override
    public String toString() {
        return name;
    }
}
