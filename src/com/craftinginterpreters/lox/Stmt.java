package com.craftinginterpreters.lox;

import java.util.List;

/**
 * Nodos del Arbol de Sintaxis Abstracta (AST) para Sentencias (Statements).
 *
 * Una sentencia representa una orden o accion que produce un EFECTO SECUNDARIO
 * (por ejemplo, imprimir en consola con 'print' o declarar variables), pero
 * NO produce un valor de retorno directamente.
 *
 * (Generado automaticamente por la herramienta GenerateAst.java)
 */
abstract class Stmt {
  interface Visitor<R> {
    R visitExpressionStmt(Expression stmt);
    R visitPrintStmt(Print stmt);
  }

  abstract <R> R accept(Visitor<R> visitor);
  static class Expression extends Stmt {
    Expression(Expr expression) {
      this.expression = expression;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitExpressionStmt(this);
    }

    final Expr expression;
  }
  static class Print extends Stmt {
    Print(Expr expression) {
      this.expression = expression;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitPrintStmt(this);
    }

    final Expr expression;
  }
}
