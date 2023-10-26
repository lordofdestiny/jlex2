package com.craftinginterpreters.lox;

import java.util.*;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    private final Interpreter interpreter;
    private final Stack<Map<String, Variable>> scopes = new Stack<>();

    private ClassType currentClass = ClassType.NONE;
    private FunctionType currentFunction = FunctionType.NONE;
    private LoopType currentLoop = LoopType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private static class Variable {
        final Token name;
        VariableState state;
        final int slot;

        Variable(Token name, VariableState state, int slot) {
            this.name = name;
            this.state = state;
            this.slot = slot;
        }
    }

    private enum VariableState {
        DECLARED, DEFINED, READ
    }

    private enum ClassType {
        NONE, CLASS
    }

    private enum FunctionType {
        NONE, FUNCTION, METHOD, INITIALIZER
    }

    private enum LoopType {
        NONE, LOOP
    }


    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() &&
            scopes.peek().containsKey(expr.name.lexeme()) &&
            scopes.peek().get(expr.name.lexeme()).state == VariableState.DECLARED) {
            Lox.error(expr.name, "Can't read local variable in it's own initializer");
        }
        resolveLocal(expr, expr.name, true);
        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name, false);
        return null;
    }

    @Override
    public Void visitFunctionExpr(Expr.Function expr) {
        resolveFunction(expr, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitConditionalExpr(Expr.Conditional expr) {
        resolve(expr.condition);
        resolve(expr.thenBranch);
        resolve(expr.elseBranch);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword,
                    "Can't use 'this' outside of a class");
        }
        resolveLocal(expr, expr.keyword, true);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        expr.arguments.forEach(this::resolve);

        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thanBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }

        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword,
                        "Can't return a value from an initializer");
            }
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt.function, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();

        resolve(stmt.statements);

        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        final var enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name);

        beginScope();
        final var scope = scopes.peek();
        scope.put("this", new Variable(null, null, scope.size()));

        for (final var method : stmt.methods) {
            final var declaration = method.name.lexeme().equals("init")
                    ? FunctionType.INITIALIZER
                    : FunctionType.METHOD;
            resolveFunction(method.function, declaration);
        }

        for (final var method : stmt.classMethod) {
            beginScope();
            final var staticScope = scopes.peek();
            staticScope.put("this", new Variable(null, null, staticScope.size()));
            resolveFunction(method.function, FunctionType.METHOD);
            endScope();
        }

        endScope();
        currentClass = enclosingClass;

        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitContinueStmt(Stmt.Continue stmt) {
        if (currentLoop != LoopType.LOOP) {
            Lox.error(stmt.keyword, "Can't use 'continue' outside a loop.");
        }
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        if (currentLoop != LoopType.LOOP) {
            Lox.error(stmt.keyword, "Can't use 'continue' outside a loop.");
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);

        final var enclosingBlock = currentLoop;
        currentLoop = LoopType.LOOP;

        resolve(stmt.body);

        currentLoop = enclosingBlock;
        return null;
    }

    private void beginScope() {
        scopes.push(new HashMap<>());
    }

    private void endScope() {
        final var scope = scopes.pop();

        scope.values().stream()
                .filter((variable) -> variable.state == VariableState.DEFINED)
                .map((variable -> variable.name))
                .forEach((name) ->
                        Lox.error(
                                name,
                                "Local variable " + name.lexeme() + " is not used"
                        )
                );
    }

    public void resolve(List<Stmt> statements) {
        statements.forEach(this::resolve);
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void resolveFunction(Expr.Function function, FunctionType type) {
        final var enclosingFunction = currentFunction;
        currentFunction = type;
        beginScope();
        if (function.parameters != null) {
            function.parameters.forEach((param) -> {
                declare(param);
                define(param);
            });
        }
        resolve(function.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) return;

        final var scope = scopes.peek();
        if (scope.containsKey(name.lexeme())) {
            Lox.error(name, "Already a variable with this name in this scope");
        }
        scope.put(name.lexeme(),
                new Variable(name,
                        VariableState.DECLARED,
                        scope.size())
        );
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;
        scopes.peek().get(name.lexeme()).state = VariableState.DEFINED;
    }

    void resolveLocal(Expr expr, Token name, boolean isRead) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            final var scope = scopes.get(i);

            if (scope.containsKey(name.lexeme())) {
                if (name.type() == TokenType.THIS) {
                    scope.put("this",
                            new Variable(name,
                                    VariableState.DEFINED,
                                    scope.get("this").slot));
                }

                interpreter.resolve(
                        expr,
                        scopes.size() - 1 - i,
                        scope.get(name.lexeme()).slot
                );

                if (isRead) {
                    scopes.get(i).get(name.lexeme()).state = VariableState.READ;
                }
            }
        }
    }
}
