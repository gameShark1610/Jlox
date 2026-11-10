package com.craftinginterpreters.lox;

import java.util.List;

/**
 * Interprete por Recorrido de Arbol (Tree-Walk Interpreter)
 *
 * Recorre el Arbol de Sintaxis Abstracta (AST) generado por el Parser y ejecuta el programa.
 *
 * CONCEPTOS CLAVE:
 *
 * 1. Patron de Diseno Visitor:
 *    - Implementa Expr.Visitor<Object> para expresiones: cada visita calcula y RETORNA un valor en memoria.
 *    - Implementa Stmt.Visitor<Void> para sentencias: cada visita EJECUTA un efecto secundario (como imprimir)
 *      y retorna null (Void).
 *    - Por que? Desacopla las clases de datos del arbol (Expr y Stmt) de la logica de evaluacion.
 *
 * 2. Recorrido Post-Orden (Evaluacion de abajo hacia arriba):
 *    - Para resolver una operacion (ej: 1 + 2), evaluate() primero resuelve los operandos hijos
 *      (1 y 2), y luego calcula el resultado en el nodo padre (+).
 *
 * 3. Despacho Doble (Double Dispatch):
 *    - 'evaluate(expr)' llama a 'expr.accept(this)'.
 *    - 'accept()' conoce el tipo exacto del nodo y le regresa la llamada al interprete:
 *      'visitor.visitBinaryExpr(this)', 'visitLiteralExpr(this)', etc.
 */
class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    private Environment environment = new Environment();

    // Punto de entrada publico del interprete: ejecuta una lista secuencial de sentencias
    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    // Auxiliar para ejecutar una sentencia enviando este interprete a su metodo accept()
    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    //Ejecuta un bloque de codigo
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double) right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            // El operador '+' está sobrecargado: suma números o concatena strings
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                }
                if (left instanceof String && right instanceof String) {
                    return (String) left + (String) right;
                }
                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double) left / (double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;
        }

        // Inalcanzable si el parser construyó bien el árbol
        return null;
    }

    // Igualdad: Soporta cualquier tipo de dato, incluso tipos mixtos (ej: nil == false es false).
    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    // Convierte un valor de runtime de Lox a un String para imprimirlo en pantalla
    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    // Convierte el nodo hoja Literal directamente en su valor primitivo en Java
    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    // Evalúa operadores unarios (! y -)
    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                // La subexpresión debe ser un número en tiempo de ejecución
                checkNumberOperand(expr.operator, right);
                return -(double) right;
            case BANG:
                return !isTruthy(right);
        }

        // Inalcanzable
        return null;
    }

    //Nos permite cuando vale una variable en determinado momento, si hacemos print x, entonces este metodo busca la x.
    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }


    // Ejecuta una sentencia de expresión: calcula el valor y lo descarta
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    // Ejecuta una sentencia 'print': calcula el valor de la expresión y lo imprime en consola
    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    //Declaracion de sentencias
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        //Si la variable tiene un inicializador, la evaluamos
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        //Si no tienen incializador, para evitar errores, la inicializamos con nil
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    //Asinga un valor a una variable
    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }


    // Evalúa una expresión enviando el intérprete actual (this) al método accept() del nodo.
    // El nodo sabrá a qué método visit* llamar de regreso.
    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    // Regla de verdad en Lox: 'false' y 'nil' son falsos; todo lo demás es verdadero (truthy).
    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean) object;
        return true;
    }

    // Verifica que el operando sea numérico antes de operaciones aritméticas (ej: -negación)
    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    // Verifica que ambos operandos sean números para operaciones binarias (ej: -, *, /, <, >)
    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }
}

