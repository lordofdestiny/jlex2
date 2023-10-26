package com.craftinginterpreters.lox;

import java.text.MessageFormat;

public record Token(TokenType type, String lexeme, Object literal, int line) {

    @Override
    public String toString() {
        return MessageFormat.format("{0} {1} {2} at {3}", type, lexeme, literal, line);
    }
}
