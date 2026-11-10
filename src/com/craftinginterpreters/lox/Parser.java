package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

/**
 * Parser por Descenso Recursivo (Recursive Descent Parser)
 *
 * Transforma una lista plana de Tokens (producida por el Scanner) en un
 * Arbol de Sintaxis Abstracta (AST) con estructura jerarquica.
 *
 * CONCEPTOS CLAVE:
 *
 * 1. Descenso Recursivo (Top-Down):
 *    - Cada regla de la gramatica formal se traduce directamente en un metodo de esta clase.
 *    - Los metodos de mayor nivel llaman a metodos de niveles mas profundos para parsear sub-expresiones.
 *
 * 2. Jerarquia de Precedencia (de menor a mayor prioridad):
 *    - expression() -> equality() -> comparison() -> term() -> factor() -> unary() -> primary()
 *    - Por que? Las reglas de MENOR precedencia (como == o +) estan ARRIBA en la pila de llamadas.
 *    - Las reglas de MAYOR precedencia (como * o parentesis) estan en el FONDO, por lo que "atan"
 *      los operandos con mas fuerza y sus nodos quedan mas profundos en el arbol (se resuelven primero).
 *
 * 3. Sentencias (Stmt) vs Expresiones (Expr):
 *    - Stmt (Statement): Representa una instruccion o accion (efecto secundario), ej: 'print', declarar variables.
 *    - Expr (Expression): Produce y calcula un valor concreto en tiempo de ejecucion, ej: '1 + 2', 'true'.
 *
 *   Declaraciones: Crean algo nuevo
 *   Sentencias: Son las que hacen algo como print o bloques
 *   Expresiones: Solo son calculos
 */
class Parser {
    //program → declaration* EOF ;

    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // Punto de entrada: Parsea una serie de sentencias completas hasta llegar al final del archivo (EOF).
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    //declaration → varDecl | statement ;
    private Stmt declaration() {
        //Hay dos tipos de statements, los que usan
        try {
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    //varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;
    private Stmt varDeclaration() {
        //Checa si si es un identificador y lo consume
        Token name = consume(IDENTIFIER, "Expect variable name.");

        //Si tiene un igual lo inicializa con la expresion dada (Recordemos que una expresion es como un 5+3, entonce sprimero ejecuta la expresion)
        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }
        //COnsume el punto y coma, y si no manda un error
        consume(SEMICOLON, "Expect ';' after variable declaration.");

        //regresa el statement con variable
        return new Stmt.Var(name, initializer);
    }

    //statement → exprStmt | printStmt |  block ;
    private Stmt statement() {
        // Si el token actual es 'print', parseamos una sentencia de impresion
        if (match(PRINT)) return printStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        // Si no es ninguna palabra clave especial, debe ser una expresion seguida de ';'
        return expressionStatement();
    }

    // exprStmt → expression ";" ;
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    //block → "{" declaration* "}" ;
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    // printStmt → "print" expression ";" ;
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    //expression → assignment ;
    private Expr expression() {
        return assignment();
    }

    //assignment → IDENTIFIER "=" assignment | equality ;
    private Expr assignment() {
        Expr expr = equality();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    // equality -> comparison ( ( "!=" | "==" ) comparison )* ;
    // Menor precedencia que comparison. Se asocia a la izquierda: 'a == b == c' se convierte en '((a == b) == c)'
    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    // Operadores de comparacion relacional.
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // term -> factor ( ( "-" | "+" ) factor )* ;
    // Suma y resta (menor prioridad que la multiplicacion, mayor que la comparacion).
    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // factor -> unary ( ( "/" | "*" ) unary )* ;
    // Multiplicacion y division (mayor prioridad que la suma/resta).
    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // unary -> ( "!" | "-" ) unary | primary ;
    // Operadores unarios con prefijo (ej: -5, !true). Se asocian a la derecha.
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    // primary -> NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")" | IDENTIFIER ;;
    // La maxima prioridad: valores atomicos directos (literales) o expresiones agrupadas entre parentesis.
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        // Si ningun caso coincide, estamos en un token que no puede iniciar una expresion sintactica valida.
        throw error(peek(), "Expect expression.");
    }

    //Check if any of the given tokens match with the current (Remember that the current reference to peek)
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    //Checks to see if the next token is of the expected type. If so, it consumes the token and everything is groovy. If some other token is there, then we’ve hit an error.
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    //Returns true if the current token is of the given type.
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    //Consumes the current token and returns it
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }
    //Checks if we’ve run out of tokens to parse.
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    //Returns the current token we have yet to consume
    private Token peek() {
        return tokens.get(current);
    }

    //Returns the most recently consumed token.
    private Token previous() {
        return tokens.get(current - 1);
    }


    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    //We want to discard tokens until we’re right at the beginning of the next statement.
    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;
            switch (peek().type) {
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
