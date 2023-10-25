package com.craftinginterpreters.lox;

class AstPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {

    String print(Expr expr) {
        return expr.accept(this);
    }

    String print(Stmt stmt) {
        return stmt.accept(this);
    }

    @Override
    public String visitCallExpr(Expr.Call expr) {
        final var builder = new StringBuilder();
        builder.append(expr.callee.accept(this)).append("(");
        for (int i = 0; i < expr.arguments.size() - 1; i++) {
            builder.append(expr.arguments.get(i).accept(this)).append(",");
        }
        if (!expr.arguments.isEmpty()) {
            builder.append(expr.arguments.get(expr.arguments.size() - 1).accept(this
            ));
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme(), expr.left, expr.right);
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        return expr.name.lexeme();
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        return expr.left.accept(this)
               + " "
               + expr.operator.lexeme()
               + " "
               + expr.right.accept(this);
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        return parenthesize("=", new Expr.Variable(expr.name), expr.value);
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme(), expr.right);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        if (expr.value instanceof String) return "\"" + expr.value + "\"";
        return expr.value.toString();
    }

    @Override
    public String visitConditionalExpr(Expr.Conditional expr) {
        return expr.condition.accept(this) +
               "? (" +
               expr.thenBranch.accept(this) +
               ") : ( " +
               expr.elseBranch.accept(this) +
               " )";
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    private String parenthesize(String name, Expr... expressions) {
        final var builder = new StringBuilder();

        builder.append("(").append(name);
        for (final var expr : expressions) {
            builder.append(" ");
            builder.append(expr.accept(this));
        }
        builder.append(")");

        return builder.toString();
    }

    public static void main(String[] args) {
        Expr expression = new Expr.Binary(
                new Expr.Unary(
                        new Token(TokenType.MINUS, "-", null, 1),
                        new Expr.Literal(123)),
                new Token(TokenType.STAR, "*", null, 1),
                new Expr.Grouping(
                        new Expr.Literal(45.67)));

        System.out.println(new AstPrinter().print(expression));
    }


    int visitBlockDepth = 0;

    @Override
    public String visitBlockStmt(Stmt.Block stmt) {
        final var builder = new StringBuilder();
        builder.append("{\n");
        visitBlockDepth++;
        for (final var stmt0 : stmt.statements) {
            builder.append("\t".repeat(visitBlockDepth)).append(stmt0.accept(this)).append("\n");
        }
        visitBlockDepth--;
        builder.append("\t".repeat(visitBlockDepth)).append("}");
        return builder.toString();
    }

    @Override
    public String visitIfStmt(Stmt.If stmt) {
        return "if ( " + stmt.condition.accept(this) + " )\n"
               + stmt.thanBranch.accept(this)
               + (stmt.elseBranch != null
                ? "else\n" + stmt.elseBranch.accept(this) + "\n"
                : ""
               );
    }

    @Override
    public String visitReturnStmt(Stmt.Return stmt) {
        return "return" + (stmt.value == null ? "" : stmt.value.accept(this));
    }

    @Override
    public String visitBreakStmt(Stmt.Break stmt) {
        return "break";
    }

    @Override
    public String visitWhileStmt(Stmt.While stmt) {
        return "while ( " + stmt.condition.accept(this) + " )\n"
               + "\t" + stmt.body.accept(this)
               + "\n";
    }

    @Override
    public String visitFunctionStmt(Stmt.Function stmt) {
        final var builder = new StringBuilder();
        builder.append("fun ").append(stmt.name.lexeme()).append("(");
        for (int i = 0; i < stmt.params.size() - 1; i++) {
            builder.append(stmt.params.get(i).lexeme()).append(",");
        }
        if (!stmt.params.isEmpty()) {
            builder.append(stmt.params.get(stmt.params.size() - 1).lexeme());
        }
        builder.append(")").append("{");
        visitBlockDepth++;
        for (final var stmt0 : stmt.body) {
            builder.append("\t".repeat(visitBlockDepth)).append(stmt0.accept(this)).append(";");
        }
        visitBlockDepth--;
        return builder.append("}").toString();
    }

    @Override
    public String visitPrintStmt(Stmt.Print stmt) {
        return "print " + stmt.expression.accept(this);
    }

    @Override
    public String visitVarStmt(Stmt.Var stmt) {
        return "var " + stmt.name.lexeme() + " = " + stmt.initializer.accept(this);
    }

    @Override
    public String visitExpressionStmt(Stmt.Expression stmt) {
        return stmt.expression.accept(this);
    }

    @Override
    public String visitContinueStmt(Stmt.Continue stmt) {
        return "continue";
    }
}
