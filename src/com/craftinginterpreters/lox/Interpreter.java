package com.craftinginterpreters.lox;

import java.util.List;

/**
 * The Tree-Walk Interpreter
 *
 * Traverses the AST produced by the Parser and executes the program.
 *
 * Key Concepts:
 * 1. Visitor Pattern:
 *    - Implements Expr.Visitor<Object> for expressions (return a runtime value).
 *    - Implements Stmt.Visitor<Void> for statements (perform side-effects, return null).
 *    - Why? Decouples the AST node data structures from the operations performed on them.
 *
 * 2. Post-Order Tree Traversal (Bottom-Up Evaluation):
 *    - To evaluate an operation (e.g. 1 + 2), evaluate() first visits the left and right children,
 *      then computes the parent's result with the evaluated values.
 *
 * 3. Double Dispatch:
 *    - 'evaluate(expr)' calls 'expr.accept(this)'.
 *    - 'accept()' knows the concrete subclass type and calls back into the matching
 *      'visitor.visitBinaryExpr(this)', 'visitLiteralExpr(this)', etc.
 */
class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    // The Interpreter's public entry point: executes a list of statements sequentially
    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    // Helper for executing a single statement via the Visitor pattern
    private void execute(Stmt stmt) {
        stmt.accept(this);
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
            //This works to concatenate two string or sum two numbers,
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

        // Unreachable.
        return null;
    }

    //The equality operators support operands of any type, even mixed ones. You can’t ask Lox if 3 is less than "three", but you can ask if it’s equal to it.
    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    //To convert a Lox value to a string
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

    //We convert the literal tree node into a runtime value.
    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    //Evaluate parentheses
    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                //The subexpression must be a number. Since we don’t statically know that in Java
                checkNumberOperand(expr.operator, right);
                return -(double) right;
            case BANG:
                return !isTruthy(right);
        }

        // Unreachable.
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }


    // Evaluates an expression by sending the interpreter (this) into the expression's accept() method.
    // The expression will call the corresponding visit* method on this interpreter.
    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }


    //False and nil are falsey, and everything else is truthy.
    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean) object;
        return true;
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }
}

