# Security policy

## Reporting a vulnerability

Please report security problems privately through GitHub:
**Security > Report a vulnerability** on this repository
(`https://github.com/EquinoxWN/rules-dsl-compiler/security/advisories/new`). Do not open a public issue.

Include what you found, how to reproduce it, and the impact you expect. You will get an answer
within 7 days. Fixes are released as soon as they are ready, and you are credited unless you ask
not to be.

## Supported versions

This project is pre-1.0. Only the latest commit on `main` receives fixes.

## Scope

- **In scope:** Input that crashes the lexer, parser or type checker (any exception or stack overflow), and rules that pass the checker but are ill-typed.
- **Out of scope:** Rules that are well-typed but wrong for your business: the checker proves types, not intent.

## How this repository protects itself

- Every GitHub Action is pinned to a full commit SHA, and workflows run with read-only
  permissions and without persisted credentials.
- Dependabot proposes dependency and action updates weekly as reviewable pull requests.
- CI runs lint, tests and a known-vulnerability check (Dependabot alerts) on every push and pull request.
