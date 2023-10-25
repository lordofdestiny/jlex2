package com.craftinginterpreters.lox;

import java.util.List;

class LoxClass implements LoxCallable {
    final String name;

    LoxClass(String name) {
        this.name = name;
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
