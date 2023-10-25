package com.craftinginterpreters.lox;

class Exit extends RuntimeException {
    Exit() {
        super(null, null, false, false);
    }
}
