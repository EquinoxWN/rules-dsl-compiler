# ADR 0003: Report every independent type error, using an ERROR type to stop cascades

- **Status:** Accepted

## Context

A checker that stops at the first type error makes people fix one mistake per save, which is
slow and frustrating for long rules. A checker that keeps going naively is worse: one unknown name
produces a stream of follow-on errors ("cannot multiply unknown by number", "cannot compare ...")
that all describe the same mistake.

## Decision

Syntax errors stop at the first one (after a syntax error, the tree is unreliable). Type checking
always visits the whole tree and collects diagnostics. When a node is ill-typed, the checker
reports it once and gives the node the special `ERROR` type; every rule that sees `ERROR` as an
operand stays silent and propagates it. Diagnostics are sorted by position before they are
returned.

## Consequences

- `if customer.age then pric * 2 else upper(price)` reports three independent mistakes at once (a
  number used as a condition, a typo with "did you mean `price`?", and a wrong argument), and a
  test pins that exact list.
- One typo produces exactly one error, however deep it is in the expression; a test checks this.
- Every node still gets a type (possibly `ERROR`), which the M3 language server will use for
  hover and completion on partially broken rules.
- Each new checking rule must remember to stay silent on `ERROR` operands; the "no cascade" test
  and the random-tree robustness test guard this.
