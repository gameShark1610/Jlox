package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

/**
 * Recursive Descent Parser
 *
 * Takes a flat list of Tokens from the Scanner and builds a hierarchical AST (Abstract Syntax Tree).
 *
 * Key Concepts:
 * 1. Recursive Descent:
 *    - Each grammar rule is represented as a method in this class.
 *    - Higher methods call lower methods to parse sub-expressions.
 *
 * 2. Precedence Hierarchy (from lowest to highest precedence):
 *    - expression() -> equality() -> comparison() -> term() -> factor() -> unary() -> primary()
 *    - Lower precedence rules are near the top of the call stack.
 *    - Higher precedence rules are at the bottom, so they bind tighter and evaluate first!
 *
 * 3. Statements vs Expressions:
 *    - Stmt (Statement): Performs an action / side effect (e.g., print, variable declaration), produces no value.
 *    - Expr (Expression): Evaluates to a runtime value (e.g., 1 + 2, true).
 */
class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    //Parses a series of statements, as many as it can find until it hits the end of the input.
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(statement());
        }

        return statements;
    }

    private Expr expression() {
        return equality();
    }

    //A program is a list of statements, and we parse one of those statements using this method
    private Stmt statement() {
        //If the current token is PRINT, we print the statement
        if (match(PRINT)) return printStatement();

        return expressionStatement();
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    // equality → comparison ( ( "!=" | "==" ) comparison )* ;
    // Lowest precedence binary operator: evaluated after comparisons, additions, and multiplications.
    private Expr equality() {
        Expr expr = comparison();

        // While we see equality operators, wrap the current expression as the left child
        // of a new Binary node (left-associative: 'a == b == c' becomes '((a == b) == c)').
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // term → factor ( ( "-" | "+" ) factor )* ;
    // Addition and subtraction (lower precedence than multiplication/division, higher than comparison).
    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // factor → unary ( ( "/" | "*" ) unary )* ;
    // Multiplication and division (binds tighter than addition/subtraction).
    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    //unary → ( "!" | "-" ) unary | primary ;
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    //primary → NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")" ;
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        //If none of the cases in there match, it means we are sitting on a token that can’t start an expression.
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
