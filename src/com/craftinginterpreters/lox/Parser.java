package com.craftinginterpreters.lox;

import javax.swing.plaf.nimbus.State;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        final var statements = new ArrayList<Stmt>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    private boolean allowExpression;
    private boolean foundExpression = false;

    Object parseRepl() {
        allowExpression = true;
        final var statements = new ArrayList<Stmt>();
        while (!isAtEnd()) {
            statements.add(declaration());

            if (foundExpression) {
                final var last = statements.get(statements.size() - 1);
                return ((Stmt.Expression) last).expression;
            }

            allowExpression = false;
        }

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(FUN)) return function("function");
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        final var name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }
        consume(SEMICOLON, "Expect ';' after variable declaration.");

        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(BREAK)) return breakStatement();
        if (match(CONTINUE)) return continueStatement();
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt breakStatement() {
        if (loopDepth == 0) {
            //noinspection ThrowableNotThrown
            error(previous(), "Must be inside a loop to use 'break'.");
        }
        consume(SEMICOLON, "Expect ';' after break.");
        return new Stmt.Break();
    }

    private Stmt continueStatement() {
        if (loopDepth == 0) {
            //noinspection ThrowableNotThrown
            error(previous(), "Must be inside a loop to use 'continue'.");
        }
        consume(SEMICOLON, "Expect ';' after continue.");
        return new Stmt.Continue();
    }

    private int loopDepth = 0;

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'");
        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(SEMICOLON)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        try {
            loopDepth++;
            Stmt body = statement();

            if (increment != null) {
                body = new Stmt.Block(
                        Arrays.asList(
                                body, new Stmt.Expression(increment)
                        )
                );
            }

            if (condition == null) condition = new Expr.Literal(true);
            // Third parameter is used for executing the loop increment after continue
            body = new Stmt.While(condition, body, new Stmt.Expression(increment));

            if (initializer != null) body = new Stmt.Block(
                    Arrays.asList(initializer, body)
            );

            return body;
        } finally {
            loopDepth--;
        }
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        final var condition = expression();
        consume(RIGHT_PAREN, "Expect '(' after if condition.");

        final var thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        final var expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Print(expr);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        final var condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        try {
            loopDepth++;
            final var body = statement();

            return new Stmt.While(condition, body, null);
        } finally {
            loopDepth--;
        }
    }

    private Stmt expressionStatement() {
        final var expr = expression();

        if (allowExpression && isAtEnd()) {
            foundExpression = true;
        } else {
            consume(SEMICOLON, "Expect ';' after value.");
        }

        return new Stmt.Expression(expr);
    }

    int functionDepth = 0;

    private Stmt.Function function(String kind) {
        final var name = consume(IDENTIFIER, "Expect " + kind + " name");
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name");
        final var parameters = new ArrayList<Token>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    //noinspection ThrowableNotThrown
                    error(peek(), "Can't have more than 255 parameters.");
                }
                parameters.add(
                        consume(IDENTIFIER, "Expect parameter name")
                );
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        try {
            functionDepth++;
            final var body = block();
            return new Stmt.Function(name, parameters, body);
        } finally {
            functionDepth--;
        }
    }

    private Stmt returnStatement() {
        if (functionDepth == 0) {
            //noinspection ThrowableNotThrown
            error(previous(), "Must be inside a function to use 'return'.");
        }
        final var keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }


    private List<Stmt> block() {
        final var statements = new ArrayList<Stmt>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        consume(RIGHT_BRACE, "Expect '}' at the end of block");
        return statements;
    }

    private Expr expression() {
        return comma();
    }

    private Expr comma() {
        var expr = conditional();
        while (match(COMMA)) {
            final var operator = previous();
            final var right = conditional();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr conditional() {
        var expr = assignment();

        if (match(QUESTION)) {
            final var thenBranch = expression();
            consume(COLON, "Expect ':' after then branch of conditional expression.");
            final var elseBranch = conditional();
            expr = new Expr.Conditional(expr, thenBranch, elseBranch);
        }
        return expr;
    }

    private Expr assignment() {
        var expr = or();

        if (match(EQUAL)) {
            final var equals = previous();
            final var value = assignment();

            if (expr instanceof Expr.Variable) {
                final var name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }

            //noinspection ThrowableNotThrown
            error(equals, "Invalid  assignment target.");
        }

        return expr;
    }

    private Expr or() {
        var expr = and();

        while (match(OR)) {
            final var operator = previous();
            final var right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        var expr = equality();

        while (match(AND)) {
            final var operator = previous();
            final var right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        var expr = comparison();
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            final var operator = previous();
            final var right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        var expr = term();
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            final var operator = previous();
            final var right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term() {
        var expr = factor();
        while (match(PLUS, MINUS)) {
            final var operator = previous();
            final var right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }


    private Expr factor() {
        var expr = unary();
        while (match(SLASH, STAR, PERCENT)) {
            final var operator = previous();
            final var right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }


    private Expr unary() {
        if (match(BANG, EQUAL)) {
            final var operator = previous();
            final var expr = primary();
            return new Expr.Unary(operator, expr);
        }
        return call();
    }

    private Expr call() {
        var expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }

        return expr;
    }

    final Expr finishCall(Expr callee) {
        var arguments = new ArrayList<Expr>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    //noinspection ThrowableNotThrown
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(conditional());
            } while (match(COMMA));
        }

        final var paren = consume(RIGHT_PAREN, "Expect ')' after arguments");

        return new Expr.Call(callee, paren, arguments);
    }

    @SuppressWarnings("ThrowableNotThrown")
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) return new Expr.Literal(previous().literal());

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            final var expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // Error productions.
        if (match(BANG_EQUAL, EQUAL_EQUAL)) {
            error(previous(), "Missing left-hand operand.");
            equality();
            return null;
        }

        if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            error(previous(), "Missing left-hand operand.");
            comparison();
            return null;
        }

        if (match(PLUS)) {
            error(previous(), "Missing left-hand operand.");
            term();
            return null;
        }

        if (match(SLASH, STAR)) {
            error(previous(), "Missing left-hand operand.");
            factor();
            return null;
        }

        throw error(peek(), "Expect expression.");
    }

    private boolean match(TokenType... types) {
        final var hasMatch = Arrays.stream(types).anyMatch(this::check);

        if (hasMatch) {
            advance();
        }

        return hasMatch;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type() == type;
    }

    private boolean isAtEnd() {
        return peek().type() == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    @SuppressWarnings("UnusedReturnValue")
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type() == SEMICOLON) return;

            switch (peek().type()) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }
}
