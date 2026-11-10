package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    private static final Interpreter interpreter = new Interpreter();
    static boolean hadError=false;
    static boolean hadRuntimeError = false;

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

    //To execute from a file
    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));
        // Indicate an error, then exit.
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    //To execute in terminal by prompts
    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);
        }
    }

    private static void run(String source) {
        // PASO 1: Analisis Lexico (Scanner)
        // Convierte la cadena de caracteres cruda en una lista de palabras/simbolos reconocibles (Tokens).
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        // PASO 2: Analisis Sintactico (Parser)
        // Toma la lista plana de tokens y construye la jerarquia de operaciones: el Arbol de Sintaxis (AST).
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // Si ocurrio algun error de sintaxis al armar el arbol, nos detenemos y no ejecutamos nada.
        if (hadError) return;

        // PASO 3: Ejecucion / Interpretacion (Interpreter)
        // Recorre el arbol de sintaxis y ejecuta cada sentencia calculando los resultados finales.
        interpreter.interpret(statements);
    }

    static void error(int line, String message) {
        report(line, "", message);
    }

    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

    //This reports an error at a given token. It shows the token’s location and the token itself.
    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    //If a runtime error is thrown while evaluating the expression, interpret() catches it.
    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() + "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }

}