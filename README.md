# jlox

## Book
[Crafting Interpreters](https://craftinginterpreters.com/contents.html)

## GitHub
https://github.com/munificent/craftinginterpreters

## Current Reading Status

### Chapter 6: Parsing Expressions
- https://craftinginterpreters.com/parsing-expressions.html#the-parser-class
- https://craftinginterpreters.com/parsing-expressions.html#syntax-errors

### Chapter 7: Evaluating Expressions
- https://craftinginterpreters.com/evaluating-expressions.html
- https://craftinginterpreters.com/evaluating-expressions.html#evaluating-unary-expressions
- https://craftinginterpreters.com/evaluating-expressions.html#truthiness-and-falsiness
- https://craftinginterpreters.com/evaluating-expressions.html#evaluating-binary-operators
- https://craftinginterpreters.com/evaluating-expressions.html#runtime-errors

### Chapter 8: Statements and State
- https://craftinginterpreters.com/statements-and-state.html
- https://craftinginterpreters.com/statements-and-state.html#global-variables
- https://craftinginterpreters.com/statements-and-state.html#parsing-variables
- https://craftinginterpreters.com/statements-and-state.html#environments
- https://craftinginterpreters.com/statements-and-state.html#interpreting-global-variables
- https://craftinginterpreters.com/statements-and-state.html#assignment
- https://craftinginterpreters.com/statements-and-state.html#assignment-syntax
- https://craftinginterpreters.com/statements-and-state.html#scope
- https://craftinginterpreters.com/statements-and-state.html#nesting-and-shadowing
- https://craftinginterpreters.com/statements-and-state.html#block-syntax-and-semantics

### Chapter 9: Control Flow
- https://craftinginterpreters.com/control-flow.html#conditional-execution
- https://craftinginterpreters.com/control-flow.html#logical-operators
- https://craftinginterpreters.com/control-flow.html#while-loops
- https://craftinginterpreters.com/control-flow.html#for-loops

## Extensions and Challenges

### Chapter 7: Evaluating Expressions
- [x] Allowed string concatenation when either operand is a string.
- [x] Added division-by-zero runtime errors.
- [x] Supported string comparison operators.

### Chapter 8: Statements and State
- [x] Extended `AstPrinter` to print statement nodes.

### Chapter 9: Control Flow
- [ ] Extended `AstPrinter` to if statement nodes.
- [ ] Added multiline statement support to the REPL by buffering incomplete input.

## Notes

- [Ch7 - Static and Dynamic Typing](./notes/ch7-static-dynamic-typing.md)
- [Ch8 - Error Recovery with Synchronize](./notes/ch8-error-recovery-with-synchronize.md)
- [Ch8 - Recursive Descent Grammar 的層級、解析順序與 Syntax / Semantics](./notes/ch8-recursive-descent-grammar.md)
- [Ch8 Challenge 1 - REPL 同時支援 Statement 與 Expression](./notes/ch8-challenge-1.md)
