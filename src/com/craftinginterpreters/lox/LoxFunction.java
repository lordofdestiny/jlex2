package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    protected final String name;
    private final Expr.Function declaration;
    private final Environment closure;
    private final boolean isInitializer;

    static class Lambda extends LoxFunction {

        Lambda(Expr.Function declaration, Environment closure) {
            super(null, declaration, closure, false);
        }

        @Override
        public String toString() {
            return "<lambda>";
        }
    }

    static class MethodBinding extends LoxFunction {
        MethodBinding(String name, Expr.Function declaration,
                      Environment closure, boolean isInitializer) {
            super(name, declaration, closure, isInitializer);
        }

        @Override
        public String toString() {
            return "<method " + name + ">";
        }
    }


    LoxFunction(String name, Expr.Function declaration, Environment closure, boolean isInitializer) {
        this.isInitializer = isInitializer;
        this.name = name;
        this.closure = closure;
        this.declaration = declaration;
    }

    public boolean isGetter() {
        return declaration.parameters == null;
    }

    LoxFunction bind(LoxInstance instance) {
        final var environment = new Environment(closure);
        environment.define(instance);
        return new LoxFunction.MethodBinding(
                instance.klass().name + "." + name,
                declaration, environment, isInitializer);
    }

    @Override
    public int arity() {
        return declaration.parameters.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        final var environment = new Environment(closure);
        if (declaration.parameters != null) {
            for (int i = 0; i < declaration.parameters.size(); i++) {
                environment.define(arguments.get(i));
            }
        }
        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            return isInitializer ?
                    closure.getAt(0, 0)
                    : returnValue.value;
        }

        if (isInitializer) return closure.getAt(0, 0);

        return null;
    }

    @Override
    public String toString() {
        return "<fn " + name + ">";
    }
}
