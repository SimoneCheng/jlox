package com.craftinginterpreters.lox;

import java.util.List;

public class AstPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {
    String print(Expr expr) {
        return expr.accept(this);
    }

    String print(List<Stmt> statements) {
        StringBuilder builder = new StringBuilder();
        for (Stmt statement : statements) {
            builder.append(statement.accept(this));
            builder.append("\n");
        }
        return builder.toString();
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesizeExpr(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesizeExpr("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesizeExpr(expr.operator.lexeme, expr.right);
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        return expr.name.lexeme;
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        return parenthesizeExpr(expr.name.lexeme, expr.value);
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        return parenthesizeExpr(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitExpressionStmt(Stmt.Expression stmt) {
        return parenthesizeExpr("expr-stmt", stmt.expression);
    }

    @Override
    public String visitPrintStmt(Stmt.Print stmt) {
        return parenthesizeExpr("print", stmt.expression);
    }

    @Override
    public String visitVarStmt(Stmt.Var stmt) {
        if (stmt.initializer != null) {
            return parenthesizeExpr("var " + stmt.name.lexeme, stmt.initializer);
        }
        return parenthesizeExpr("var " + stmt.name.lexeme);
    }

    @Override
    public String visitIfStmt(Stmt.If stmt) {
        StringBuilder builder = new StringBuilder();

        builder.append("(if");
        builder.append(" ");
        builder.append(stmt.condition.accept(this));
        builder.append(" ");
        builder.append(parenthesizeStmt("then", stmt.thenBranch));
        if (stmt.elseBranch != null) {
            builder.append(" ");
            builder.append(parenthesizeStmt("else", stmt.elseBranch));
        }
        builder.append(")");

        return builder.toString();
    }

    @Override
    public String visitWhileStmt(Stmt.While stmt) {
        StringBuilder builder = new StringBuilder();

        builder.append("(while");
        builder.append(" ");
        builder.append(stmt.condition.accept(this));
        builder.append(" ");
        builder.append(stmt.body.accept(this));
        builder.append(")");

        return builder.toString();
    }

    @Override
    public String visitBlockStmt(Stmt.Block stmt) {
        return parenthesizeStmt("block", stmt.statements.toArray(Stmt[]::new));
    }

    private String parenthesizeStmt(String name, Stmt... statements) {
        StringBuilder builder = new StringBuilder();

        builder.append("(").append(name);
        for (Stmt statement : statements) {
            builder.append(" ");
            builder.append(statement.accept(this));
        }
        builder.append(")");

        return builder.toString();
    }

    private String parenthesizeExpr(String name, Expr... exprs) {
        StringBuilder builder = new StringBuilder();

        builder.append("(").append(name);
        for (Expr expr : exprs) {
            builder.append(" ");
            builder.append(expr.accept(this));
        }
        builder.append(")");

        return builder.toString();
    }

    public static void main(String[] args) {
        Expr expression = new Expr.Binary(
                new Expr.Unary(
                        new Token(TokenType.MINUS, "-", null, 1),
                        new Expr.Literal(123)),
                new Token(TokenType.STAR, "*", null, 1),
                new Expr.Grouping(
                        new Expr.Literal(45.67)));

        System.out.println(new AstPrinter().print(expression));
    }
}
