package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;

public class Lox {
    private static final Interpreter interpreter = new Interpreter();
    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));

        // Indicate an error in the exit code
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        final var input = new InputStreamReader(System.in);
        final var reader = new BufferedReader(input);

        while (true) {
            System.out.print("> ");
            final var line = reader.readLine();
            if (line.strip().replaceAll(";", "").equals("exit()")) {
                break;
            }
            final var scanner = new Scanner(line);
            final var tokens = scanner.scanTokens();
            final var parser = new Parser(tokens);
            final var syntax = parser.parseRepl();

            if (hadError) return;

            if (syntax instanceof List<?>) {
                @SuppressWarnings("unchecked") final var statements = (List<Stmt>) syntax;
                interpreter.interpret(statements);
            } else {
                final var result = interpreter.interpret((Expr) syntax);
                if (result != null) {
                    System.out.println("= " + result);
                }
            }

            hadError = false;
        }
    }

    private static void run(String source) {
        final var scanner = new Scanner(source);
        final var tokens = scanner.scanTokens();
        final var parser = new Parser(tokens);
        final var statements = parser.parse();

        // Stop if there was a syntax error.
        if (hadError) return;

        final var resolver = new Resolver(interpreter);
        resolver.resolve(statements);

        // Stop if there was a resolution error
        if (hadError) return;

        interpreter.interpret(statements);
    }

    static void error(int line, String message) {
        report(line, "", message);
    }

    static void error(Token token, String message) {
        if (token.type() == TokenType.EOF) {
            report(token.line(), " at end ", message);
        } else {
            report(token.line(), " at '" + token.lexeme() + "'", message);
        }
    }

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() + "\n[line " + error.token.line() + "]");
        hadRuntimeError = true;
    }

    private static void report(int line, String where, String message) {
        System.err.println(
                MessageFormat.format(
                        "[line {0}] Error{1}: {2}",
                        line, where, message)
        );
        hadError = true;
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }
}