# Design Note: Static and Dynamic Typing

## 原文

Some languages, like Java, are statically typed which means type errors are detected and reported at compile time before any code is run. Others, like Lox, are dynamically typed and defer checking for type errors until runtime right before an operation is attempted. We tend to consider this a black-and-white choice, but there is actually a continuum between them.

It turns out even most statically typed languages do some type checks at runtime. The type system checks most type rules statically, but inserts runtime checks in the generated code for other operations.

For example, in Java, the static type system assumes a cast expression will always safely succeed. After you cast some value, you can statically treat it as the destination type and not get any compile errors. But downcasts can fail, obviously. The only reason the static checker can presume that casts always succeed without violating the language’s soundness guarantees, is because the cast is checked at runtime and throws an exception on failure.

A more subtle example is **covariant arrays** in Java and C#. The static subtyping rules for arrays allow operations that are not sound. Consider:

```java
Object[] stuff = new Integer[1];
stuff[0] = "not an int!";
```

This code compiles without any errors. The first line upcasts the Integer array and stores it in a variable of type Object array. The second line stores a string in one of its cells. The Object array type statically allows that—strings are Objects—but the actual Integer array that `stuff` refers to at runtime should never have a string in it! To avoid that catastrophe, when you store a value in an array, the JVM does a runtime check to make sure it’s an allowed type. If not, it throws an ArrayStoreException.

Java could have avoided the need to check this at runtime by disallowing the cast on the first line. It could make arrays invariant such that an array of Integers is not an array of Objects. That’s statically sound, but it prohibits common and safe patterns of code that only read from arrays. Covariance is safe if you never write to the array. Those patterns were particularly important for usability in Java 1.0 before it supported generics. James Gosling and the other Java designers traded off a little static safety and performance—those array store checks take time—in return for some flexibility.

There are few modern statically typed languages that don’t make that trade-off somewhere. Even Haskell will let you run code with non-exhaustive matches. If you find yourself designing a statically typed language, keep in mind that you can sometimes give users more flexibility without sacrificing too many of the benefits of static safety by deferring some type checks until runtime.

On the other hand, a key reason users choose statically typed languages is because of the confidence the language gives them that certain kinds of errors can never occur when their program is run. Defer too many type checks until runtime, and you erode that confidence.

## ChatGPT 5.6 Sol Medium 解釋

這一段其實是在補一個很重要的觀念：**「靜態型別語言」和「動態型別語言」不是完全二分法，而比較像一條光譜。**

你可以先把前面的基本區分抓住：

* **靜態型別**：很多型別錯誤在程式真正執行前就檢查掉，例如 Java。
* **動態型別**：很多型別檢查會拖到程式執行到那一行時才做，例如 Lox。

但作者接著說：**即使是 Java 這種靜態型別語言，也不代表「所有型別問題都在 compile time 解決」。**

最簡單的例子就是 cast：

```java
Object x = "hello";
Integer y = (Integer) x;
```

Java 編譯器看到：

```java
(Integer) x
```

不會直接跟你說「這一定錯」。

因為 `x` 的 static type 是 `Object`，理論上它 runtime 裡面可能真的裝的是 `Integer`。

所以這段可以通過編譯。

但真正跑到這一行時，JVM 發現：

```text
x 真正裝的是 String
```

於是 runtime 才丟 `ClassCastException`。

所以作者的意思是：

> Java 的 static type checker 在某些地方會說：「好，我先相信你，但真正執行時 JVM 要再檢查一次。」

---

文章裡更有趣的是這個 array 例子：

```java
Object[] stuff = new Integer[1];
stuff[0] = "not an int!";
```

第一行竟然是合法的：

```java
Object[] stuff = new Integer[1];
```

因為 Java 認為：

```text
Integer 是 Object
↓
Integer[] 也可以視為 Object[]
```

這叫做 **array covariance（陣列協變）**。

所以現在：

```java
stuff
```

它的 **static type** 是：

```java
Object[]
```

因此編譯器看到：

```java
stuff[0] = "not an int!";
```

會想：

```text
stuff 是 Object[]
String 是 Object
那當然可以放啊
```

所以 compile 成功。

但問題來了：

```java
Object[] stuff = new Integer[1];
```

真正被建立出來的物件其實仍然是：

```java
Integer[]
```

你可以想成：

```text
stuff ────────┐
              ▼
        ┌────────────┐
        │ Integer[]  │
        │ [   ]      │
        └────────────┘
```

只是 `stuff` 這個變數現在「用 Object[] 的角度」看它。

因此如果真的讓：

```java
stuff[0] = "hello";
```

成功，底下會變成：

```text
Integer[]
┌────────────┐
│ "hello"    │  ← 啊？
└────────────┘
```

那就破壞了 `Integer[]` 應該只能裝 Integer 的保證。

所以 JVM 在**真正寫入 array 的瞬間**還要檢查：

```text
這個 array runtime 真正的型別是什麼？
```

發現是：

```java
Integer[]
```

而你要塞的是：

```java
String
```

於是丟：

```text
ArrayStoreException
```

---

所以這裡最好把 **static type** 和 **runtime type** 分開看：

```java
Object[] stuff = new Integer[1];
```

這時：

```text
變數 stuff 的 static type
        ↓
     Object[]

真正建立的 object 的 runtime type
        ↓
     Integer[]
```

這兩者並不一定一樣。

而這正是為什麼有些事情 compiler 不知道，只能交給 runtime。

---

那為什麼 Java 不乾脆禁止：

```java
Object[] stuff = new Integer[1];
```

呢？

其實可以。

Java 當初如果規定：

```text
Integer[] 不是 Object[]
```

就完全不會有這個問題。

這叫做讓 array **invariant**。

但這樣下面這種事情就會變麻煩：

```java
void printAll(Object[] objects) {
    for (Object o : objects) {
        System.out.println(o);
    }
}

Integer[] nums = {1, 2, 3};

printAll(nums);
```

這個 function 只是**讀取** array，完全沒有寫入，其實非常安全。

如果 array invariant，就不能直接把 `Integer[]` 傳給接受 `Object[]` 的函式。

所以 Java 當初做了一個 trade-off：

```text
允許 Integer[] → Object[]
        ↓
比較方便、比較有彈性

但因此 static type system 不完全 sound
        ↓
所以 JVM 寫入 array 時補 runtime check
```

也就是文章說的：

> traded off a little static safety and performance ... in return for some flexibility

因為 runtime check 也是需要一點成本的。

---

其中有一句：

> Covariance is safe if you never write to the array.

這其實非常關鍵。

假設：

```java
Object[] stuff = new Integer[10];
```

如果你只做：

```java
Object x = stuff[0];
```

完全沒問題。

因為：

```text
Integer 一定也是 Object
```

所以「讀」是安全的。

真正危險的是：

```java
stuff[0] = someObject;
```

因為：

```text
someObject 是 Object
```

不代表它是：

```text
Integer
```

這個「read safe / write unsafe」的概念之後你如果看到 generics 的：

```java
? extends T
? super T
```

其實會再次遇到，跟 variance 有很直接的關係。

---

最後作者真正想講的是語言設計上的 trade-off。

可以想成一條線：

```text
compile time checking                    runtime checking
        │                                      │
        ▼                                      ▼
更多靜態保證 ------------------------------ 更多彈性
```

假設一個語言什麼都要求 compile time 證明：

```text
優點：
很多 bug 根本不可能跑到 production

缺點：
type system 可能很嚴格
有些其實安全的程式也寫不了
```

反過來，如果很多事情 runtime 才檢查：

```text
優點：
比較靈活
程式比較容易寫

缺點：
你可能跑到那一行才炸掉
```

所以作者說：

> 靜態 vs 動態，不要把它想成黑白二選一。

比較像：

```text
            static checks
                 ↓
C++ ─ Java ─ Rust ─ Haskell ...
                 ↑
            runtime checks
```

每個語言只是把「哪些東西 compile time 保證、哪些東西 runtime 檢查」的界線畫在不同地方。

而這跟你現在看的 interpreter 很有關係，因為 Lox 的：

```lox
"hello" - 3
```

compiler/parser 並不會阻止它。

Interpreter 跑到 `-` 的時候才做：

```text
左邊是不是 number？
右邊是不是 number？
```

不是的話才報 runtime error。

Java 則可能把其中很多檢查提前到 compile time。

所以整段 Design Note 的核心其實可以濃縮成一句：

> **靜態型別不是「沒有 runtime type checking」，而是「盡可能把某些型別錯誤提前檢查」；實際的語言通常會在靜態安全、彈性與執行成本之間做取捨。**

而作者用 Java array covariance，就是在展示一個很典型的例子：**Java 故意讓 static type system 稍微寬鬆一點，再靠 JVM runtime check 把洞補起來。**

## Resource

- https://craftinginterpreters.com/evaluating-expressions.html#design-note
