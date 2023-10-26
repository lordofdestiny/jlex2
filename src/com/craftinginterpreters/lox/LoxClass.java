package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class LoxClass extends LoxInstance implements LoxCallable {
    final String name;
    final LoxClass superClass;
    private final Map<String, LoxFunction> methods;

    LoxClass(LoxClass metaClass, LoxClass superclass, String name, Map<String, LoxFunction> methods) {
        super(metaClass);
        this.superClass = superclass;
        this.name = metaClass == null
                ? "<metaclass " + name + ">"
                : name;
        this.methods = methods;
    }

    Optional<LoxFunction> findMethod(String name) {
        return Optional.ofNullable(methods.getOrDefault(name, null))
                .or(() -> Optional.ofNullable(methods.getOrDefault(name, null)));
    }

    @Override
    public int arity() {
        return findMethod("init")
                .map(LoxFunction::arity)
                .orElse(0);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        final var instance = new LoxInstance(this);
        findMethod("init")
                .map((init) -> init.bind(instance)).
                ifPresent((init) -> init.call(interpreter, arguments));
        return instance;
    }

    @Override
    public String toString() {
        return klass == null ? name : "<class " + name + ">";
    }
}
