package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;
import static com.craftinginterpreters.lox.TokenType.*;

/*
program        → statement* EOF ;
statement      → exprStmt | printStmt ;
exprStmt       → expression ";" ;
printStmt      → "print" expression ";" ;
expression     → equality ;
equality       → comparison ( ( "!=" | "==" ) comparison )* ;
comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term           → factor ( ( "-" | "+" ) factor )* ;
factor         → unary ( ( "/" | "*" ) unary )* ;
unary          → ( "!" | "-" ) unary
               | primary ;
primary        → NUMBER | STRING | "true" | "false" | "nil"
               | "(" expression ")" ;
*/

public class Parser {
  private static class ParseError extends RuntimeException {}
  private static final boolean SHOW_TRACE = false;
  private final List<Token> tokens;
  private int current = 0;
  private int traceDepth = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  List<Stmt> parse() {
    traceEnter("parse");
    List<Stmt> statements = new java.util.ArrayList<>();
    while (!isAtEnd()) {
      statements.add(statement());
    }
    traceExit("parse");
    return statements;
  }

  private Stmt statement() {
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

  private Expr expression() {
    traceEnter("expression");
    Expr result = equality();
    traceExit("expression");
    return result;
  }

  private Expr equality() {
    traceEnter("equality");
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    traceExit("equality");
    return expr;
  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }

    return false;
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private Expr comparison() {
    traceEnter("comparison");
    Expr expr = term();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }

    traceExit("comparison");
    return expr;
  }

  private Expr term() {
    traceEnter("term");
    Expr expr = factor();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    traceExit("term");
    return expr;
  }

  private Expr factor() {
    traceEnter("factor");
    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    traceExit("factor");
    return expr;
  }

  private Expr unary() {
    traceEnter("unary");
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      Expr result = new Expr.Unary(operator, right);
      traceExit("unary");
      return result;
    }

    Expr result = primary();
    traceExit("unary");
    return result;
  }

  private Expr primary() {
    traceEnter("primary");
    if (match(FALSE)) {
      Expr result = new Expr.Literal(false);
      traceExit("primary");
      return result;
    }
    if (match(TRUE)) {
      Expr result = new Expr.Literal(true);
      traceExit("primary");
      return result;
    }
    if (match(NIL)) {
      Expr result = new Expr.Literal(null);
      traceExit("primary");
      return result;
    }

    if (match(NUMBER, STRING)) {
      Expr result = new Expr.Literal(previous().literal);
      traceExit("primary");
      return result;
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      Expr result = new Expr.Grouping(expr);
      traceExit("primary");
      return result;
    }

    throw error(peek(), "Expect expression.");
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();

    throw error(peek(), message);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  private void traceEnter(String function) {
    if (SHOW_TRACE) {
      System.out.println("  ".repeat(traceDepth) + "> " + function + " | next = " + peek());
      traceDepth++;
    }
  }

  private void traceExit(String function) {
    if (SHOW_TRACE) {
      traceDepth--;
      System.out.println("  ".repeat(traceDepth) + "< " + function + " | next = " + peek());
    }
  }

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
