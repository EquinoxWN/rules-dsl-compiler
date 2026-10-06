# ADR 0002: Hand-written Pratt parser instead of a parser generator

- **Status:** Accepted

## Context

The users of this language are not engineers. When a rule is wrong, the error message is the
product: it has to say what is wrong in plain words, underline exactly the code at fault, and
suggest the fix. Parser generators such as ANTLR produce a correct parser quickly, but their
default errors ("mismatched input ')' expecting {...}") are written for engineers, recovering
good spans means mapping their token stream back to the source, and they add a runtime library.
The language is also small: about 15 operators, three keyword forms and literals.

## Decision

Write the lexer and parser by hand. The parser is a Pratt (top-down operator precedence)
parser: every infix operator has a left and a right binding power in one table, prefix and
postfix operators are handled in two small functions, and comparisons are deliberately
non-associative so `18 <= age < 65` is an error with the hint "join them with `and`". Every AST
node carries a `Span`. Nesting is limited to 200 levels, counted both on recursion depth and on
tree height, so chains built in the loop (`1+1+1...`) cannot overflow the checker's stack either.

## Consequences

- Every syntax error names what was expected and what was found, with a tailored hint for the
  common cases (unclosed parenthesis, chained comparison, `!` instead of `not`).
- Precedence lives in one table, and the precedence tests mirror it line by line; a mutation that
  makes `+` right-associative is caught.
- No runtime dependency: the library is plain Java 21.
- The cost is maintenance: grammar changes are code changes, and there is no grammar file to
  hand to other tools. The grammar is documented in RFC 0001 instead, and the round-trip property
  test (print then parse is the identity over 3,000 random trees) guards against drift between
  parser and printer.
