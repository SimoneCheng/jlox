# REPL 同時支援 Statement 與 Expression

## Challenge 原文

> The REPL no longer supports entering a single expression and automatically printing its result value. That’s a drag. Add support to the REPL to let users type in both statements and expressions. If they enter a statement, execute it. If they enter an expression, evaluate it and display the result value.

## Challenge 目標

目前正常的 Lox program 是從：

```text
program → declaration* EOF ;
```

開始解析。

而 expression statement 則要求：

```text
exprStmt → expression ";" ;
```

所以正常程式中：

```lox
1 + 2;
```

是合法的 expression statement。

但：

```lox
1 + 2
```

因為沒有 `;`，不能被當成 expression statement。

Challenge 希望 REPL 額外支援：

```text
> 1 + 2
3

> 1 == 2
false
```

也就是：

* 輸入 **statement** → execute
* 輸入單獨的 **expression** → evaluate，並直接顯示結果

但這個改動應該只影響 REPL，不應該改變正常 `.lox` 程式的 grammar。

因此不能單純把：

```text
exprStmt → expression ";"
```

改成分號 optional。

---

# 1. Parser 原本只有 Program Entry Point

原本 Parser 的主要入口：

```java
List<Stmt> parse() {
  List<Stmt> statements = new ArrayList<>();

  while (!isAtEnd()) {
    statements.add(declaration());
  }

  return statements;
}
```

可以把它理解成：

```text
parse()
  ↓
parse program
  ↓
declaration*
  ↓
List<Stmt>
```

所以外部只要呼叫：

```java
parser.parse();
```

整份輸入就一定會被當成 program / statements 解析。

例如：

```lox
1 + 2
```

流程會變成：

```text
parse()
↓
declaration()
↓
statement()
↓
expressionStatement()
↓
expression()
↓
成功 parse 1 + 2
↓
consume(SEMICOLON)
↓
失敗
```

因此問題其實不是：

> Parser 不會解析 `1 + 2`

而是：

> Parser 目前只有一個「把輸入當 program」的入口。

---

# 2. 為 Parser 增加 Expression Entry Point

Parser 本來就已經有：

```java
private Expr expression()
```

而且它可以正確建立 expression AST。

例如：

```lox
1 + 2
```

可以產生：

```text
Binary
├── Literal(1)
├── +
└── Literal(2)
```

因此可以再提供一個給外部使用的 entry point：

```java
Expr parseExpression() {
  return expression();
}
```

概念上就變成：

```text
Parser
├── parse()
│     ↓
│   program
│     ↓
│   List<Stmt>
│
└── parseExpression()
      ↓
    expression
      ↓
    Expr
```

同一個 Parser class 有兩種解析入口，不需要另外寫一個 Parser class。

---

# 3. Expression 必須吃完整份輸入

如果 `parseExpression()` 只有：

```java
Expr parseExpression() {
  return expression();
}
```

會有一個問題。

例如：

```lox
a = 3;
```

`expression()` 可以成功解析：

```lox
a = 3
```

但最後的：

```text
;
```

還沒有被消耗。

如果這時直接回傳成功，就會把：

```lox
a = 3;
```

錯誤地判斷成 REPL 的裸 expression。

因此真正要判斷的是：

> 整份 input 能不能完整地解析成「一個 expression」。

概念上應該是：

```text
expression EOF
```

例如：

```text
1 + 2 EOF
      ↑
      ✅ expression 剛好吃完整份輸入
```

但：

```text
1 + 2 ; EOF
        ↑
        ❌ expression 後面還剩 ;
```

所以後者應該改走正常的 statement / program parser。

---

# 4. REPL 先嘗試 Expression，再嘗試 Program

REPL 可以採用兩階段解析：

```text
輸入
 │
 ▼
先嘗試完整 parse 成 expression
 │
 ├── 成功
 │     ↓
 │   evaluate
 │     ↓
 │   print result
 │
 └── 失敗
       ↓
    重新建立 Parser
       ↓
    parse program
       ↓
    execute statements
```

例如：

```text
> 1 + 2
```

第一條路直接成功：

```text
parseExpression()
↓
Expr.Binary
↓
evaluate
↓
3
```

但：

```text
> print a;
```

expression parser 一開始看到：

```text
PRINT
```

就知道：

```text
這不是 expression
```

因此改走：

```text
parse()
↓
statement()
↓
printStatement()
```

即可正常執行。

---

# 5. 為什麼需要重新建立 Parser？

Parser 裡有：

```java
private int current = 0;
```

它記錄目前讀到哪個 token。

如果第一次：

```text
parseExpression()
```

已經消耗了一部分 token 才失敗，就不能直接拿同一個 Parser 繼續：

```text
parse()
```

因為 `current` 已經不在開頭。

因此 fallback 時最簡單的方式是：

```java
parser = new Parser(tokens);
```

讓第二次 parsing 重新從：

```text
current = 0
```

開始。

所以：

```text
第一次 Parser
→ 嘗試 expression

失敗

第二次 Parser
→ 從頭 parse program
```

---

# 6. 最大的問題：ParseError 不能當成真正的 Syntax Error

原本 Parser 的：

```java
private ParseError error(Token token, String message) {
  Lox.error(token, message);
  return new ParseError();
}
```

其實做了兩件不同的事情：

```text
① 建立 / 拋出 ParseError
② 呼叫 Lox.error() 報告真正的 syntax error
```

這兩件事情平常放在一起沒有問題。

例如真正錯誤的程式：

```lox
var a = ;
```

parser 發現 syntax 不合法：

```text
error()
↓
Lox.error(...)
↓
顯示錯誤
↓
ParseError
```

很合理。

---

但 REPL 的 expression parsing 是一種 **probe（試探）**。

例如：

```lox
print a;
```

expression parser 看到 `print` 時會失敗。

但是：

```text
expression parser 失敗
```

並不等於：

```text
使用者寫錯 syntax
```

它只代表：

> 我猜這份輸入是 expression，但猜錯了，它可能其實是 statement。

所以：

```text
「不是 expression」
```

和：

```text
「這份程式有 syntax error」
```

是兩件不同的事情。

---

# 7. 為什麼一開始 `print` 和 `var` 都不能正常執行？

一開始 expression probe 失敗時仍然呼叫：

```java
Lox.error(...)
```

例如：

```text
> var a = 1;
```

流程：

```text
parseExpression()
↓
primary()
↓
看到 VAR
↓
expression grammar 不接受 VAR
↓
error()
↓
Lox.error(...)
↓
hadError = true
↓
ParseError
```

雖然後面可能重新建立 Parser：

```text
new Parser(tokens)
↓
parse()
↓
varDeclaration()
↓
語法其實完全合法
```

但是前一次 expression probe 已經留下：

```text
hadError = true
```

因此這次「試探失敗」污染了真正的 parsing state。

結果就可能出現：

```text
> var a = 1;
ERROR: Expect expression.

> a
Undefined variable 'a'.
```

也就是 `var a = 1;` 最後根本沒有真的被 interpret。

---

# 8. Silent Expression Probe

因此 expression probe 必須是 **silent** 的。

概念上 Parser 需要區分：

```text
Normal Parsing
↓
遇到 error
↓
Lox.error()
↓
真正報錯
```

和：

```text
Expression Probe
↓
遇到 ParseError
↓
不要 report
↓
只代表：
「這不是 expression，換 program parser 試看看」
```

例如可以讓 Parser 有：

```java
private boolean reportErrors = true;
```

真正 parsing 時：

```text
reportErrors = true
```

而 expression probe 時：

```text
reportErrors = false
```

`error()` 則變成概念上的：

```java
private ParseError error(Token token, String message) {
  if (reportErrors) {
    Lox.error(token, message);
  }

  return new ParseError();
}
```

因此：

```text
> var a = 1;
```

第一次：

```text
parseExpression()
↓
失敗
↓
ParseError
↓
不 report
↓
不污染 hadError
```

第二次：

```text
parse()
↓
varDeclaration()
↓
interpret
↓
environment.define("a", 1)
```

接著：

```text
> a
1
```

就能正常工作。

---

# 9. ParseError 和真正的 Error Reporting 是不同概念

這次實作讓 `ParseError` 的角色變得很清楚。

`ParseError` 可以只是 Parser 內部的 **control flow mechanism**：

```text
「目前這條 grammar route 走不通」
```

但：

```java
Lox.error(...)
```

代表的是：

```text
「使用者真的寫了一個非法的 Lox program」
```

所以：

```text
ParseError
≠
一定要向使用者報錯
```

在 expression probe 裡：

```text
ParseError
↓
只代表試探失敗
```

在正式 program parsing 裡：

```text
ParseError
↓
代表真正 syntax error
↓
需要 report
```

---

# 10. 和 Panic Mode / Error Recovery 的關係

前面 parser 章節有做 error recovery。

例如：

```java
private Stmt declaration() {
  try {
    ...
  } catch (ParseError error) {
    synchronize();
    return null;
  }
}
```

它的目的不是：

> 發生一個 syntax error 就立刻停止整份 parsing。

而是：

```text
發生 syntax error
↓
進入 panic mode
↓
synchronize()
↓
找到下一個可能的 statement boundary
↓
繼續 parse 後面的程式
```

例如：

```lox
var a = ;

print "hello";

var b = 3;
```

即使第一行有 syntax error，parser 還是希望能跳到後面的：

```lox
print "hello";
var b = 3;
```

繼續解析。

這樣 compiler / interpreter 才可能一次找到多個 syntax errors，而不是每次只報一個。

因此：

```text
ParseError
```

本身不一定代表：

```text
整個 parser 必須立即終止
```

它也可以是一種：

> 從目前 grammar rule 跳出去，找到可以重新同步的位置。

---

這和 REPL challenge 裡的做法有一點相似：

```text
Expression Probe
↓
ParseError
↓
這條解析方式不成立
↓
改嘗試另一條解析方式
```

只是目的不同。

Panic mode 是：

```text
同一份 program 中
找到下一個可以繼續 parsing 的位置
```

REPL probe 則是：

```text
同一份 input
改用另一個 parsing entry point
```

兩者共同的觀念都是：

> **Parsing failure 不一定代表整個 parsing process 必須立刻死亡。**

---

# 11. Interpreter 本來就已經會 Evaluate Expression

Interpreter 中原本就有：

```java
private Object evaluate(Expr expr) {
  return expr.accept(this);
}
```

所以它早就可以處理：

```text
Expr.Binary
Expr.Unary
Expr.Literal
Expr.Grouping
Expr.Variable
Expr.Assign
```

例如：

```lox
1 == 2
```

AST：

```text
Binary
├── 1
├── ==
└── 2
```

呼叫：

```text
evaluate(Binary)
↓
visitBinaryExpr()
↓
false
```

因此 challenge 不需要增加：

```java
visitExpressionExpr()
```

因為 `expression` 本身不是 AST node。

它只是所有 expression 類型的總稱：

```text
Expr
├── Binary
├── Unary
├── Literal
├── Grouping
├── Variable
└── Assign
```

只需要提供一個讓 REPL 能取得 expression evaluation result 的 method 即可。

---

# 12. Expression Statement 和 REPL Expression 的差異

正常程式：

```lox
1 + 2;
```

會被 parse 成：

```text
Stmt.Expression
└── Expr.Binary
```

Interpreter：

```java
public Void visitExpressionStmt(Stmt.Expression stmt) {
  evaluate(stmt.expression);
  return null;
}
```

也就是：

```text
evaluate
↓
得到 3
↓
把結果丟掉
```

所以正常 script 不會印出：

```text
3
```

這是正確的。

但 REPL：

```text
> 1 + 2
```

希望：

```text
Expr.Binary
↓
evaluate
↓
3
↓
print
```

因此兩者最大的差異不是 expression 怎麼計算，而是：

```text
普通 Expression Statement
→ evaluate 後丟掉 value

REPL 裸 Expression
→ evaluate 後顯示 value
```

---

# 13. 最後的 REPL Parsing 流程

完成後可以整理成：

```text
使用者輸入一行
        │
        ▼
      Scanner
        │
        ▼
      Tokens
        │
        ▼
建立 Parser
        │
        ▼
Silent Expression Probe
        │
   ┌────┴────┐
   │         │
成功         失敗
   │         │
   ▼         ▼
 Expr      不是真正 error
   │         │
   ▼         ▼
Evaluate   重新建立 Parser
   │         │
   ▼         ▼
Print      parse program
             │
             ▼
          List<Stmt>
             │
             ▼
           Execute
```

測試結果：

```text
> 1 + 2
3

> 1 == 2
false

> 1 == 1
true

> 1 + 2;

> var a = 1;

> print a;
1

> a
1
```

其中：

```text
1 + 2
```

是裸 expression，因此 REPL 自動顯示結果。

但：

```text
1 + 2;
```

是 expression statement，因此只是 execute，不自動印出值。

---

# 核心整理

這個 challenge 最重要的不是修改 expression grammar，而是：

> **同一份 token 可以從不同的 parser entry point 解析，而 REPL 可以先試探它是不是完整 expression，再 fallback 到正常 program parsing。**

原本的 grammar：

```text
exprStmt → expression ";"
```

完全不用改。

原本：

```java
consume(SEMICOLON, ...)
```

也完全不用拿掉。

因為：

```text
正常 Lox program
```

和：

```text
REPL interactive input
```

本來就可以有不同的 parsing entry behavior。

同時這題也讓 `ParseError` 和 error reporting 的差異變得更明顯：

```text
ParseError
→ parser 某條解析路徑失敗

Lox.error()
→ 使用者真的寫出了 syntax error
```

REPL expression probe 的失敗只是：

```text
「這不是 expression」
```

而不是：

```text
「這是一份錯誤的程式」
```

所以 probe 必須 silent，不能污染 `hadError`。

而這也和之前學到的 panic mode / synchronization 有共同核心：

> **Parser 遇到一次 parsing failure，不一定就要放棄整個 parsing process；有時候可以重新同步、繼續後面的程式，有時候則可以改走另一個合法的 parsing route。**

## Resource

* 作者的答案：https://github.com/munificent/craftinginterpreters/blob/master/note/answers/chapter08_statements.md
* [Crafting Interpreters — Statements and State](https://craftinginterpreters.com/statements-and-state.html)
* [Crafting Interpreters — Parsing Expressions](https://craftinginterpreters.com/parsing-expressions.html)
