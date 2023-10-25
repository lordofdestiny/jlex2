package com.craftinginterpreters.lox;

class LoxInstance {
    private final LoxClass klass;

    public LoxInstance(LoxClass klass) {
        this.klass = klass;
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }
}
