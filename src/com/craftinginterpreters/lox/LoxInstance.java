package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class LoxInstance {
    protected final LoxClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    public LoxInstance(LoxClass klass) {
        this.klass = klass;
    }

    LoxClass klass() {
        return klass;
    }

    Object get(Token name) {
        if (fields.containsKey(name.lexeme())) {
            return fields.get(name.lexeme());
        }

        return klass.findMethod(name.lexeme())
                .map((method) -> method.bind(this))
                .orElseThrow(() -> new RuntimeError(name,
                        "Undefined property '" + name.lexeme() + "'."));
    }

    void set(Token name, Object value) {
        fields.put(name.lexeme(), value);
    }

    private String toString(int level, LoxInstance caller) {
        final var builder = new StringBuilder();
        builder.append("<").append(klass.name).append(">");
        if (!fields.isEmpty()) {
            builder.append(" {");
        }
        for (final var field : fields.entrySet()) {
            final var key = field.getKey();
            final var value = field.getValue();
            builder.append("\n").append("\t".repeat(level)).append(key).append(" : ");

            if (value instanceof LoxInstance inst) {
                if (inst == caller) {
                    builder.append("<").append(inst.klass.name).append("> {...}");
                } else {
                    builder.append(inst.toString(level + 1, this));
                }
            } else {
                builder.append(Interpreter.stringify(value));
            }

            builder.append(",");
        }
        if (!fields.isEmpty()) {
            builder.setLength(builder.length() - 1);
            builder.append("\n").append("\t".repeat(level - 1)).append("}");
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return toString(1, this);
    }
}
