package com.craftinginterpreters.lox;

public class Token {
    final TokenType type;
    final String lexeme;
    final Object literal;
    final int line;

    //Example
    /*
    var resultado = "hola"; // Línea 5
    this.type = STRING
    this.lexeme = "\"hola\""
    this.literal = "hola"
    this.line = 5
    */

    public Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;//
        this.literal = literal;//Lexeme without quotes
        this.line = line;
    }


    @Override
    public String toString() {
        return type + " " + lexeme +" " + literal;
    }
}
