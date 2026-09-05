# Error Recovery with `synchronize()`

## 原文

This `declaration()` method is the method we call repeatedly when parsing a series of statements in a block or a script, so it’s the right place to synchronize when the parser goes into panic mode. The whole body of this method is wrapped in a try block to catch the exception thrown when the parser begins error recovery. This gets it back to trying to parse the beginning of the next statement or declaration.

The real parsing happens inside the try block. First, it looks to see if we’re at a variable declaration by looking for the leading `var` keyword. If not, it falls through to the existing `statement()` method that parses `print` and expression statements.

Remember how `statement()` tries to parse an expression statement if no other statement matches? And `expression()` reports a syntax error if it can’t parse an expression at the current token? That chain of calls ensures we report an error if a valid declaration or statement isn’t parsed.

## ChatGPT 5.6 Sol Medium 解釋

這一段主要是在講 **parser 遇到 syntax error 之後，怎麼恢復並繼續往下 parse，而不是第一個錯誤就讓整個 parsing 中止。**

假設我們有這段 Lox：

```lox
var a = ;
print "hello";
var b = 10;
````

第一行：

```lox
var a = ;
```

是不合法的。

parser 在看到 `=` 之後，會預期後面是一個 expression，但現在直接遇到 `;`，所以會產生 syntax error，並丟出 `ParseError`。

如果 parser 完全沒有 error recovery，錯誤一路往外傳之後，整個 parsing 就可能直接結束。

這樣的結果會是：

```text
var a = ;        ← 發現錯誤，停止
print "hello";   ← 沒有繼續 parse
var b = 10;      ← 沒有繼續 parse
```

對使用者來說並不方便，因為每修掉一個 syntax error，重新執行之後才會看到下一個錯誤。

所以 Crafting Interpreters 使用了 **panic mode error recovery**。

大致流程是：

```text
正常 parsing
    ↓
發現 syntax error
    ↓
丟出 ParseError
    ↓
回到 declaration()
    ↓
catch ParseError
    ↓
synchronize()
    ↓
跳過目前已經無法可靠解析的 token
    ↓
找到下一個可能的 statement / declaration 邊界
    ↓
繼續 parsing
```

這裡很重要的是：

**`synchronize()` 並不是要修正原本的 syntax error。**

它做的事情比較像是：

> 「我已經不知道剛剛那段程式到底是什麼了，那就先放棄這一段，往後找一個比較確定可以重新開始 parse 的位置。」

例如在：

```lox
var a = ;
print "hello";
```

第一行出錯之後，parser 不應該隨便從中間某個 token 繼續，因為這時 parser 的狀態可能已經和實際程式結構不同步。

所以 `synchronize()` 會往後移動，直到看到一些很可能代表「新的 statement / declaration 開始了」的 token，例如：

```text
class
fun
var
for
if
while
print
return
```

或者前一個 token 是：

```text
;
```

因為分號通常表示前一個 statement 已經結束。

這樣 parser 就可以重新取得一個比較可靠的起點。

---

之所以把這個 error recovery 放在 `declaration()`，是因為現在 parser 的高層結構開始變成：

```text
program
    ↓
declaration
declaration
declaration
...
```

也就是 grammar 裡的：

```text
program → declaration* EOF ;
```

parser 會反覆呼叫：

```java
declaration()
```

來解析整份 script 裡的一個個 declaration 或 statement。

而 `declaration()` 裡面又會決定：

```text
是不是 var declaration？
        ↓
是 → varDeclaration()

不是
 ↓
statement()
```

所以它其實是很多高層語句的共同入口。

因此它很適合作為一個 **error recovery boundary**：

```text
第一個 declaration
        ↓
      成功

第二個 declaration
        ↓
      出錯
        ↓
   synchronize()
        ↓

第三個 declaration
        ↓
      繼續
```

這樣一個 declaration 出錯，只需要放棄目前這一段，不需要連後面的整份程式一起放棄。

---

這也解釋了為什麼作者是在這一章才真正把 `synchronize()` 接進 parsing 流程。

前面的章節其實已經介紹並實作過 `synchronize()`，但當時 parser 主要還是在處理 expression，以及比較單純的 statement，還沒有現在這種很明確的：

```text
program → declaration*
```

高層結構。

到了這一章加入 variable declaration 之後，作者新增了：

```java
declaration()
```

而 `declaration()` 會被反覆呼叫，用來解析一整串 statement 和 declaration。

因此現在就有了一個很自然的位置可以寫：

```java
private Stmt declaration() {
    try {
        if (match(VAR)) return varDeclaration();

        return statement();
    } catch (ParseError error) {
        synchronize();
        return null;
    }
}
```

也就是：

```text
try
 ↓
嘗試解析一個完整的 declaration / statement

如果失敗
 ↓
catch ParseError
 ↓
synchronize()
 ↓
回到下一個 declaration / statement 的起點
```

所以 `ParseError` 在這裡其實不只是「表示錯了」。

它也被拿來做 **control flow**：

```text
底層 expression 發現錯誤
        ↓
throw ParseError
        ↓
一路跳出目前這個 parsing branch
        ↓
回到 declaration()
        ↓
統一進行 recovery
```

這樣就不用讓 `expression()`、`term()`、`factor()`、`primary()` 等每一層 parser rule 都各自處理「出錯後我要怎麼繼續」。

錯誤可以一路傳到一個比較高層、邊界比較明確的位置，再統一恢復。

---

所以這一段最重要的觀念可以濃縮成：

> **`synchronize()` 的目的不是修正 syntax error，而是在 parser 出錯後放棄目前已經無法可靠解析的部分，重新找到下一個 statement / declaration 的邊界，讓 parser 可以繼續處理後面的程式；這一章加入 `declaration()` 後，它成為反覆解析高層語句的共同入口，因此也成為最適合統一進行 error recovery 的位置。**

## Resource

- https://craftinginterpreters.com/statements-and-state.html#parsing-variables
