# Recursive Descent Grammar 的層級、解析順序與 Syntax / Semantics

## 原文

目前實作完第八章之後， Lox 的 grammar 如下：

```text
program        → statement* EOF ;

declaration    → varDecl
               | statement ;

varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;

statement      → exprStmt
               | printStmt
               | block ;

block          → "{" declaration* "}" ;

exprStmt       → expression ";" ;

printStmt      → "print" expression ";" ;

expression     → assignment ;

assignment     → IDENTIFIER "=" assignment
               | equality ;

equality       → comparison ( ( "!=" | "==" ) comparison )* ;

comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;

term           → factor ( ( "-" | "+" ) factor )* ;

factor         → unary ( ( "/" | "*" ) unary )* ;

unary          → ( "!" | "-" ) unary
               | primary ;

primary        → NUMBER
               | STRING
               | "true"
               | "false"
               | "nil"
               | "(" expression ")"
               | IDENTIFIER ;
```

## ChatGPT 5.6 Sol Medium 解釋

這份 grammar 看起來像是一層一層往下排列，因此很容易產生一個直覺：

> 越下面的 grammar rule 是不是就越先被 parser 解析？

這個理解有一部分是對的，但需要把兩件事情分開：

1. **Recursive descent parser 的呼叫與 AST 建立順序**
2. **Operator precedence 的高低**

---

### 1. Recursive descent parser 是從上層一路往下呼叫

假設程式是：

```lox
print 1 + 2 * 3;
```

Parser 通常會從比較大的程式結構開始：

```text
program()
    ↓
declaration()
    ↓
statement()
    ↓
printStatement()
    ↓
expression()
    ↓
assignment()
    ↓
equality()
    ↓
comparison()
    ↓
term()
    ↓
factor()
    ↓
unary()
    ↓
primary()
```

也就是說，**method call 的方向是從上層 grammar 往下層 grammar 走。**

例如：

```text
program
→ statement
→ expression
→ assignment
→ equality
→ comparison
→ term
→ factor
→ unary
→ primary
```

直到最下面的 `primary()`，parser 才真正碰到像：

```text
NUMBER
STRING
IDENTIFIER
```

這些最基本的 syntax。

例如：

```lox
1 + 2 * 3
```

最底層會先辨認：

```text
1
2
3
```

然後 recursive call 開始 return。

AST 也是在 return 的過程中一層一層組起來。

---

### 2. AST 是從底層往上組回來的

對：

```lox
1 + 2 * 3
```

parser 最後需要建立：

```text
       +
      / \
     1   *
        / \
       2   3
```

不是：

```text
       *
      / \
     +   3
    / \
   1   2
```

實際上可以想成：

```text
先辨認：

1
2
3

↓

factor() 發現：

2 * 3

所以先建立：

    *
   / \
  2   3

↓

term() 再處理：

1 + (2 * 3)

所以建立：

      +
     / \
    1   *
       / \
      2   3
```

因此可以說：

> **Recursive descent 的 method call 是從上往下，而 AST 通常是在 recursion return 時從下往上建立。**

---

### 3. 「越下面優先順序越高」只適用於 expression precedence grammar

像：

```text
assignment
equality
comparison
term
factor
unary
primary
```

這一段的上下層級確實是在表示 operator precedence。

大致上：

```text
低 precedence
      ↓

assignment      =
equality        == !=
comparison      > >= < <=
term            + -
factor          * /
unary           ! -
primary         literal、identifier、(...)

      ↑
高 precedence
```

因此：

```text
factor
```

會比：

```text
term
```

更緊密地組合 operand。

例如：

```lox
1 + 2 * 3
```

grammar 是：

```text
term → factor ( ("-" | "+") factor )*
```

也就是 `+` 的 operand 是一個完整的 `factor`。

而：

```text
factor → unary ( ("/" | "*") unary )*
```

因此右邊的：

```lox
2 * 3
```

會先被 `factor()` 吃掉。

最後 `term()` 看到的其實比較像：

```text
1 + [2 * 3]
```

所以自然得到：

```lox
1 + (2 * 3)
```

---

### 4. 但 `program`、`statement`、`block` 不是 operator precedence

例如：

```text
program
declaration
statement
block
exprStmt
printStmt
```

這些上下關係不是：

```text
block 比 statement precedence 高
```

這種意思。

它們描述的是：

> **程式結構是怎麼由其他語法結構組成的。**

例如：

```text
program → declaration* EOF ;
```

意思是：

> 一個 program 可以由很多 declaration 組成。

而：

```text
statement → exprStmt
          | printStmt
          | block ;
```

意思是：

> 一個 statement 可以是一個 expression statement、print statement 或 block。

再看：

```text
block → "{" declaration* "}" ;
```

代表：

> 一個 block 裡面又可以包含很多 declaration。

因此這部分比較像：

```text
Program
│
├── Declaration
│   │
│   ├── Variable Declaration
│   │
│   └── Statement
│       │
│       ├── Print Statement
│       ├── Expression Statement
│       └── Block
│           │
│           └── Declaration
│
└── ...
```

是在描述程式的 **nesting / composition**。

不是 precedence。

---

### 5. 那 grammar 的層級還是需要設計正確嗎？

需要。

雖然：

```text
program
declaration
statement
```

不是 operator precedence，但它們之間仍然存在語法上的 dependency。

例如：

```text
exprStmt → expression ";" ;
```

代表：

> expression statement 裡面包含 expression。

所以 recursive descent parser 自然會：

```text
expressionStatement()
        ↓
expression()
```

而不是反過來。

同樣：

```text
statement → block ;
```

所以：

```text
statement()
    ↓
block()
```

而：

```text
block → "{" declaration* "}"
```

又會：

```text
block()
   ↓
declaration()
```

所以會形成 recursive structure：

```text
declaration()
    ↓
statement()
    ↓
block()
    ↓
declaration()
    ↓
statement()
    ↓
block()
    ↓
...
```

這正是為什麼 Lox 可以寫巢狀 block：

```lox
{
  var a = 1;

  {
    var b = 2;

    {
      print a + b;
    }
  }
}
```

也就是 grammar 本身描述了：

```text
block
  └── declaration
        └── statement
              └── block
                    └── declaration
                         ...
```

---

### 6. 所以「grammar 要按照解析順序排」嗎？

比較精確的說法不是：

> grammar 必須按照文件中由上到下的順序排列。

而是：

> **grammar rule 之間的 dependency 必須設計正確。**

文字寫在哪一行本身通常沒有意義。

真正重要的是：

```text
A → B
```

代表：

> 要辨識 A 時，可能需要辨識 B。

在 recursive descent parser 裡通常就會變成：

```java
A() {
    B();
}
```

所以 grammar：

```text
exprStmt → expression ";" ;
```

會變成類似：

```java
private Stmt expressionStatement() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(value);
}
```

也就是：

```text
Grammar dependency

exprStmt
   ↓
expression


Parser call

expressionStatement()
   ↓
expression()
```

---

### 7. 兩種「往下」其實代表不同的事情

因此目前的 grammar 可以大致分成兩部分。

#### Program structure

```text
program
↓
declaration
↓
statement
↓
exprStmt / printStmt / block
↓
expression
```

這裡的「往下」表示：

> **大的 syntax structure 是由比較小的 syntax structure 組成的。**

例如：

```text
Program
contains
Statements

Statement
may contain
Expression
```

---

#### Expression precedence

```text
expression
↓
assignment
↓
equality
↓
comparison
↓
term
↓
factor
↓
unary
↓
primary
```

這裡除了 composition 之外，還刻意利用 grammar 層級表示：

> **operator precedence。**

越往下面：

```text
operator binding 越緊
```

例如：

```text
+
↓
*

所以：

1 + 2 * 3

↓

1 + (2 * 3)
```

---

### 8. Syntax 和 Semantics

這裡也可以順便區分兩個很容易混在一起的概念：

```text
Syntax
vs.
Semantics
```

最簡單可以記：

> **Syntax：程式「能不能這樣寫」。**

> **Semantics：這樣寫「代表什麼意思」。**

#### Syntax

Syntax 是 grammar 主要負責描述的東西。

例如：

```lox
1 + 2;
```

符合：

```text
exprStmt → expression ";"
```

所以 syntax 合法。

但：

```lox
1 + ;
```

不合法。

因為：

```text
term → factor ( "+" factor )*
```

`+` 後面應該還要有：

```text
factor
```

但現在直接碰到：

```text
;
```

因此 parser 無法建立合法的 syntax tree。

這就是：

```text
Syntax Error
```

#### Semantics

但是有些程式：

```text
syntax 完全合法
```

執行起來卻沒有合理的語意。

例如 Lox：

```lox
"hello" - 3;
```

從 grammar 來看完全合法。

Parser 可以建立：

```text
Binary
├── Literal("hello")
├── -
└── Literal(3)
```

所以：

```text
Syntax：OK
```

但 Interpreter 執行 `-` 時會要求：

```text
left operand  → number
right operand → number
```

結果現在是：

```text
String - Number
```

所以發生 runtime error。

也就是：

```text
Syntax：合法

Semantics：不合法
```

#### 另一個例子：變數

```lox
a = 3;
```

從 grammar 看：

```text
assignment → IDENTIFIER "=" assignment
```

所以 syntax 合法。

但是如果程式裡根本沒有宣告：

```lox
var a;
```

那 Interpreter 在 runtime 查 environment 時可能發現：

```text
Undefined variable 'a'.
```

這也是 semantic 層面的問題。

Parser 只知道：

```text
IDENTIFIER "=" expression
```

長得像合法 assignment。

但它不知道：

```text
這個 identifier 到底有沒有對應到一個存在的 variable？
```

這需要語言後面的 semantic processing 才知道。

#### 可以把整個流程想成

```text
Source Code
    │
    ▼
 Scanner
    │
    ▼
 Tokens
    │
    ▼
 Parser
    │
    │ 主要處理
    ▼
 Syntax
    │
    ▼
 AST
    │
    ▼
 Resolver / Interpreter
    │
    │ 處理程式真正的意義
    ▼
 Semantics
```

其中：

```text
Parser：
「這句程式是不是符合語言文法？」

Interpreter：
「既然這句程式合法，那它實際上要做什麼？」
```

---

### 9. 核心整理

Recursive descent parser 可以想成：

```text
從高階 syntax 開始呼叫
        ↓
一路遞迴到最基本的 syntax
        ↓
辨認 token
        ↓
recursive call 開始 return
        ↓
AST 一層一層往上組起來
```

但是 grammar 中的「上下層級」有兩種不同的用途：

```text
Program Structure

program
↓
declaration
↓
statement
↓
expression

表示：
大的 syntax structure
由哪些較小 structure 組成
```

以及：

```text
Expression Precedence

assignment
↓
equality
↓
comparison
↓
term
↓
factor
↓
unary
↓
primary

表示：
expression 如何組合
以及 operator precedence
```

因此不能單純說：

> **所有 grammar 都是「越下面 precedence 越高」。**

比較精確的是：

> **Recursive descent parser 會依照 grammar 的 dependency 從高階結構一路呼叫到較基本的結構，再在 return 時建立 AST；而 expression grammar 額外利用這種分層來表達 operator precedence。**

另外：

```text
Syntax
→ 程式能不能這樣寫

Semantics
→ 這段合法程式實際上代表什麼、執行時應該做什麼
```

例如：

```lox
1 + ;
```

是：

```text
Syntax Error
```

而：

```lox
"hello" - 3;
```

則是：

```text
Syntax OK
Semantic / Runtime Error
```

這兩者是 compiler / interpreter 中非常重要的不同層次。

## Resource

* [Crafting Interpreters — Parsing Expressions](https://craftinginterpreters.com/parsing-expressions.html)
* [Crafting Interpreters — Statements and State](https://craftinginterpreters.com/statements-and-state.html)
