package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;
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

    final Environment globals = new Environment();

    private Environment environment = globals;
    private static final Object uninitialized = new Object();

    private static abstract class NativeFunction implements LoxCallable {
        @Override
        public String toString() {
            return "<native fn>";
        }
    }

    Interpreter() {
        globals.define("clock", new NativeFunction() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
            }

        });

        globals.define("input", new NativeFunction() {
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

        globals.define("number", new NativeFunction() {
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
            statements.forEach(this::execute);
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

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
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

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        final var left = evaluate(expr.left);
        final var right = evaluate(expr.right);

        return switch (expr.operator.type()) {
            case GREATER -> {
                checkNumberOperands(expr.operator, left, right);
                yield (double) left > (double) right;
            }
            case GREATER_EQUAL -> {
                checkNumberOperands(expr.operator, left, right);
                yield (double) left >= (double) right;
            }
            case LESS -> {
                checkNumberOperands(expr.operator, left, right);
                yield (double) left < (double) right;
            }
            case LESS_EQUAL -> {
                checkNumberOperands(expr.operator, left, right);
                yield (double) left <= (double) right;
            }
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
            default -> null; // Unreachable
        };
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        final var value = environment.get(expr.name);
        if (value == uninitialized) {
            throw new RuntimeError(expr.name, "Variable must be initialized before use.");
        }
        return value;
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
        environment.assign(expr.name, value);
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
        return isTruthy(evaluate(expr.condition)) ? evaluate(expr.thenBranch) : evaluate(expr.elseBranch);
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

        environment.define(stmt.name.lexeme(), value);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
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
        environment.define(stmt.name.lexeme(), function);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        final var value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }
}
