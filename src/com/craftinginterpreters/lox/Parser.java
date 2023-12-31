package com.craftinginterpreters.lox;

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
            if (match(CLASS)) return classDeclaration();
            if (check(FUN) && checkNext(IDENTIFIER)) {
                consume(FUN, null);
                return function("function");
            }
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt classDeclaration() {
        final var name = consume(IDENTIFIER, "Expect class name");

        Expr.Variable superclass = null;
        if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name.");
            superclass = new Expr.Variable(previous());
        }

        consume(LEFT_BRACE, "Expect '{' before class body.");

        final var methods = new ArrayList<Stmt.Function>();
        final var classMethods = new ArrayList<Stmt.Function>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            (match(STATIC) ? classMethods : methods).add(function("method"));
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.");

        return new Stmt.Class(name, superclass, methods, classMethods);
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
        if (check(SUPER) && checkNext(LEFT_PAREN)) {
            consume(SUPER, null);
            return superStatement();
        }
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt breakStatement() {
        final var keyword = consume(SEMICOLON, "Expect ';' after break.");
        return new Stmt.Break(keyword);
    }

    private Stmt continueStatement() {
        final var keyword = consume(SEMICOLON, "Expect ';' after continue.");
        return new Stmt.Continue(keyword);
    }

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

        if (initializer != null) body = new Stmt.Block(Arrays.asList(initializer, body));

        return body;
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
        final var body = statement();

        return new Stmt.While(condition, body, null);
    }

    private Stmt superStatement() {
        final var keyword = previous();
        consume(LEFT_PAREN, "Expect '(' for super call.");
        final var expr = finishCall(new Expr.Super(
                        keyword, new Token(IDENTIFIER,
                        "init", null, keyword.line()
                ))
        );
        consume(SEMICOLON, "Expected ';' after constructor 'super' call.");
        return new Stmt.InitSuper(keyword, expr);
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

    private Stmt.Function function(String kind) {
        final var name = consume(IDENTIFIER, "Expect " + kind + " name");
        return new Stmt.Function(name, functionBody(kind));
    }

    private Expr.Function functionBody(String kind) {

        List<Token> parameters = null;
        if (!kind.equals("method") || check(LEFT_PAREN)) {
            consume(LEFT_PAREN, "Expect '(' after " + kind + " name");
            parameters = new ArrayList<>();
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
        }

        if (match(ARROW)) {
            final var arrow = previous();
            final var expr = expression();
            if (kind.equals("method")) {
                consume(SEMICOLON, "Expect ';' after lambda getter.");
            }
            return new Expr.Function(parameters, List.of(
                    new Stmt.Return(arrow, expr)
            ));
        } else {
            consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
            final var body = block();
            return new Expr.Function(parameters, body);
        }
    }

    private Stmt returnStatement() {
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

            if (expr instanceof Expr.Variable variable) {
                return new Expr.Assign(variable.name, value);
            } else if (expr instanceof Expr.Get get) {
                return new Expr.Set(get.object, get.name, value);
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
            } else if (match(DOT)) {
                final var name = consume(IDENTIFIER,
                        "Expect property name after '.'");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }

        return expr;
    }

    final Expr.Call finishCall(Expr callee) {
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
        if (match(FUN)) return functionBody("function");
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);
        if (match(NUMBER, STRING)) return new Expr.Literal(previous().literal());
        if (match(SUPER)) {
            final var keyword = previous();
            consume(DOT, "Expect '.' after 'super'");
            final var method = consume(IDENTIFIER,
                    "Expect superclass method name.");
            return new Expr.Super(keyword, method);
        }
        if (match(THIS)) return new Expr.This(previous());
        if (match(IDENTIFIER)) return new Expr.Variable(previous());

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

    private boolean checkNext(TokenType type) {
        if (isAtEnd()) return false;
        if (tokens.get(current + 1).type() == EOF) return false;
        return tokens.get(current + 1).type() == type;
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
