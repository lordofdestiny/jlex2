package com.craftinginterpreters.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }

        final var outputDir = args[0];

        defineAst(outputDir, "Expr", getExpressionsHashMap());
        defineAst(outputDir, "Stmt", getStatementsHashMap());
    }

    private static HashMap<String, String> getExpressionsHashMap() {
        final var expressions = new HashMap<String, String>();
        expressions.put("Assign", "Token name, Expr value");
        expressions.put("Binary", "Expr left, Token operator, Expr right");
        expressions.put("Call", "Expr callee, Token paren, List<Expr> arguments");
        expressions.put("Get", "Expr object, Token name");
        expressions.put("Grouping", "Expr expression");
        expressions.put("Function", "List<Token> parameters, List<Stmt> body");
        expressions.put("Literal", "Object value");
        expressions.put("Logical", "Expr left, Token operator, Expr right");
        expressions.put("Unary", "Token operator, Expr right");
        expressions.put("Variable", "Token name");
        expressions.put("Conditional", "Expr condition, Expr thenBranch, Expr elseBranch");
        return expressions;
    }

    private static HashMap<String, String> getStatementsHashMap() {
        final var statements = new HashMap<String, String>();
        statements.put("Block", "List<Stmt> statements");
        statements.put("Class", "Token name, List<Stmt.Function> methods");
        statements.put("Break", "Token keyword");
        statements.put("Continue", "Token keyword");
        statements.put("Expression", "Expr expression");
        statements.put("Function", "Token name, Expr.Function function");
        statements.put("If", "Expr condition, Stmt thanBranch, Stmt elseBranch");
        statements.put("Print", "Expr expression");
        statements.put("Return", "Token keyword, Expr value");
        statements.put("Var", "Token name, Expr initializer");
        statements.put("While", "Expr condition, Stmt body, Stmt forIncrement");
        return statements;
    }

    @SuppressWarnings("SameParameterValue")
    private static void defineAst(String outputDir, String baseName, Map<String, String> types) throws IOException {
        final var path = outputDir + File.separator + baseName + ".java";
        try (final var writer = new PrintWriter(path, StandardCharsets.UTF_8)) {
            writer.println("package com.craftinginterpreters.lox;");

            writer.println();
            writer.println("import java.util.List;");
            writer.println();
            writer.println("abstract class " + baseName + " {");

            defineVisitor(writer, baseName, types);

            for (var entries : types.entrySet()) {
                final var className = entries.getKey();
                final var fields = entries.getValue();
                defineType(writer, baseName, className, fields);
            }

            // The base accept() method
            writer.println();
            writer.println("\t abstract <R> R accept(Visitor<R> visitor);");

            writer.println("}");
        }
    }

    private static void defineVisitor(PrintWriter writer, String baseName, Map<String, String> types) {
        writer.println("\tinterface Visitor<R> {");

        for (String typeName : types.keySet()) {
            writer.println(
                    "\t\tR visit" + typeName + baseName + "(" +
                    typeName + " " + baseName.toLowerCase() +
                    ");"
            );
        }

        writer.println("\t}");
    }

    private static void defineType(PrintWriter writer, String baseName, String className, String fieldList) {
        writer.println("\tstatic class " + className + " extends " + baseName + "{ ");

        var fields = fieldList.isEmpty() ? new String[0] : fieldList.split(", ");

        // Constructor
        writer.println("\t\t" + className + "(" + fieldList + ") {");
        for (var field : fields) {
            var name = field.split(" ")[1];
            writer.println("\t\t\tthis." + name + " = " + name + ";");
        }
        writer.println("\t\t}");

        // Visitor pattern
        writer.println();
        writer.println("\t\t @Override");
        writer.println("\t\t <R> R accept(Visitor<R> visitor) {");
        writer.println("\t\t\treturn visitor.visit" + className + baseName + "(this);");
        writer.println("\t\t}");

        writer.println();
        // Fields
        for (var field : fields) {
            writer.println("\t\tfinal " + field + ";");
        }

        writer.println("\t}");
    }
}
