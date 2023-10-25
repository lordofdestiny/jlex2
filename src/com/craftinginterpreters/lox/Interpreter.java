package com.craftinginterpreters.lox;

import java.util.*;
import java.util.Scanner;
import java.util.stream.Collectors;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private static class BreakException extends RuntimeException {
        BreakException() {
            super(null, null, false, false);
        }
    }

    private static class ContinueException extends RuntimeException {
        ContinueException() {
            super(null, null, false, false);
        }
    }

    final HashMap<String, Object> globals = new HashMap<>();
    private Environment environment;
    private final Map<Expr, Integer> locals = new HashMap<>();
    private final Map<Expr, Integer> slots = new HashMap<>();

    private static final Object uninitialized = new Object();

    private static abstract class NativeFunction implements LoxCallable {
        @Override
        public String toString() {
            return "<native fn>";
        }
    }

    Interpreter() {
        globals.put("exit", new NativeFunction() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                throw new Exit();
            }
        });

        globals.put("clock", new NativeFunction() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
            }

        });

        globals.put("input", new NativeFunction() {
            private static final Scanner sc = new Scanner(System.in);

            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return sc.nextLine();
            }
        });

        globals.put("number", new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                final var string = arguments.get(0);

                if (!(string instanceof String)) {
                    return null;
                }
                try {
                    return Double.valueOf((String) string);
                } catch (NumberFormatException nfe) {
                    return null;
                }
            }
        });
    }

    void interpret(List<Stmt> statements) {
        try {
            try {
                statements.forEach(this::execute);
            } catch (Exit exit) {
                // Do nothing
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    String interpret(Expr expression) {
        try {
            final var value = evaluate(expression);
            return stringify(value);
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
            return null;
        }
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    public void resolve(Expr expr, int depth, int slot) {
        locals.put(expr, depth);
        slots.put(expr, slot);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        var previous = this.environment;
        try {
            this.environment = environment;
            statements.forEach(this::execute);
        } finally {
            this.environment = previous;
        }
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            var text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        final var callee = evaluate(expr.callee);
        final var arguments = expr.arguments
                .stream().map(this::evaluate)
                .collect(Collectors.toCollection(ArrayList::new));

        if (!(callee instanceof LoxCallable function)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes");
        }

        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren,
                    "Expected " + function.arity() +
                    " arguments but got" + arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    private Boolean handleComparison(Token operator, Double left, Double right) {
        return switch (operator.type()) {
            case GREATER -> left > right;
            case GREATER_EQUAL -> left >= right;
            case LESS -> left < right;
            case LESS_EQUAL -> left <= right;
            default -> null;
        };
    }

    private boolean compareNumbersAndStrings(Token operator, Object left, Object right) {
        if (left instanceof String && right instanceof String) {
            return handleComparison(operator,
                    (double) ((String) left).compareTo((String) right), 0.0);
        }
        if (left instanceof Double && right instanceof Double) {
            return handleComparison(operator, (Double) left, (Double) right);
        }
        if (left instanceof String && right instanceof Number ||
            left instanceof Number && right instanceof String) {
            return handleComparison(operator,
                    (double) left.toString().compareTo(right.toString()), 0.0
            );
        }
        throw new RuntimeError(operator,
                "Only strings or numbers are comparable");
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        final var left = evaluate(expr.left);
        final var right = evaluate(expr.right);

        return switch (expr.operator.type()) {
            case GREATER, GREATER_EQUAL,
                    LESS, LESS_EQUAL -> compareNumbersAndStrings(expr.operator, left, right);
            case BANG_EQUAL -> !isEqual(left, right);
            case EQUAL_EQUAL -> isEqual(left, right);
            case PLUS -> {
                if (left instanceof String || right instanceof String) {
                    yield stringify(left) + stringify(right);
                }

                if (left instanceof Double && right instanceof Double) {
                    yield (double) left + (double) right;
                }

                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            }
            case MINUS -> {
                checkNumberOperands(expr.operator, left, right);
                yield (double) left - (double) right;
            }
            case SLASH -> {
                checkNumberOperands(expr.operator, left, right);
                yield (double) left / (double) right;
            }
            case STAR -> {
                checkNumberOperands(expr.operator, left, right);
                yield (double) left * (double) right;
            }
            case PERCENT -> {
                checkNumberOperands(expr.operator, left, right);
                yield (double) left % (double) right;
            }
            case COMMA -> right;
            default -> null; // Unreachable
        };
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        final var object = lookUpVariable(expr.name, expr);
        if (object == uninitialized) {
            throw new RuntimeError(expr.name, "Variable used before initialization");
        }
        return object;
    }

    private Object lookUpVariable(Token name, Expr.Variable expr) {
        final var distance = locals.get(expr);

        if (distance != null) {
            return environment.getAt(distance, slots.get(expr));
        } else {
            if (globals.containsKey(name.lexeme())) {
                return globals.get(name.lexeme());
            } else {
                throw new RuntimeError(name,
                        "Undefined variable '" + name.lexeme() + "'.");
            }
        }
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        final var left = evaluate(expr.left);

        if (expr.operator.type() == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        final var value = evaluate(expr.value);

        final var distance = locals.get(expr);

        if (distance != null) {
            environment.assignAt(distance, slots.get(expr), value);
        } else {
            if (globals.containsKey(expr.name.lexeme())) {
                globals.put(expr.name.lexeme(), value);
            } else {
                throw new RuntimeError(expr.name,
                        "Undefined variable '" + expr.name.lexeme() + "'.");
            }
        }

        return value;
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr) {
        return new LoxFunction(null, expr, environment);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        final var right = evaluate(expr.right);

        return switch (expr.operator.type()) {
            case BANG -> !isTruthy(right);
            case MINUS -> {
                checkNumberOperand(expr.operator, right);
                yield -(double) right;
            }
            default -> null; // Unreachable
        };
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitConditionalExpr(Expr.Conditional expr) {
        return isTruthy(evaluate(expr.condition))
                ? evaluate(expr.thenBranch)
                : evaluate(expr.elseBranch);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operator must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be numbers");
    }

    private boolean isEqual(Object left, Object right) {
        if (left == null && right == null) return true;
        if (left == null) return false;
        return left.equals(right);
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean) object;
        return true;
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = uninitialized;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        define(stmt.name, value);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        final var klass = new LoxClass(stmt.name.lexeme());
        define(stmt.name, klass);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thanBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);
        throw new Return(value);
    }


    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new BreakException();
    }

    @Override
    public Void visitContinueStmt(Stmt.Continue stmt) {
        throw new ContinueException();
    }


    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        try {
            while (isTruthy(evaluate(stmt.condition))) {
                try {
                    execute(stmt.body);
                } catch (ContinueException ce) {
                    if (stmt.forIncrement != null) {
                        execute(stmt.forIncrement);
                    }
                }
            }

        } catch (BreakException be) {
            // Do nothing
        }

        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        final var function = new LoxFunction(stmt.name.lexeme(), stmt.function, environment);
        define(stmt.name, function);
        return null;
    }

    private void define(Token name, Object value) {
        if (environment != null) {
            environment.define(value);
        } else {
            globals.put(name.lexeme(), value);
        }
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        final var value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }
}
