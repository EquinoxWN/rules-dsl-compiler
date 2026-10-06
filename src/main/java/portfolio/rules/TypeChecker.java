package portfolio.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import portfolio.rules.Ast.Arm;
import portfolio.rules.Ast.Binary;
import portfolio.rules.Ast.BinaryOp;
import portfolio.rules.Ast.BoolLit;
import portfolio.rules.Ast.Call;
import portfolio.rules.Ast.Expr;
import portfolio.rules.Ast.If;
import portfolio.rules.Ast.Let;
import portfolio.rules.Ast.ListLit;
import portfolio.rules.Ast.LiteralPattern;
import portfolio.rules.Ast.Match;
import portfolio.rules.Ast.Member;
import portfolio.rules.Ast.Name;
import portfolio.rules.Ast.NumberLit;
import portfolio.rules.Ast.Paren;
import portfolio.rules.Ast.TextLit;
import portfolio.rules.Ast.Unary;
import portfolio.rules.Ast.UnaryOp;
import portfolio.rules.Ast.Wildcard;
import portfolio.rules.Type.EnumT;
import portfolio.rules.Type.ListT;
import portfolio.rules.Type.RecordT;

/**
 * Infers the type of every node bottom-up and reports every mistake it finds, not just the
 * first. Nodes that already failed get the ERROR type, which silences follow-on errors.
 */
final class TypeChecker {
    private final Schema schema;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Map<Expr, Type> types = new IdentityHashMap<>();
    private final List<Map.Entry<String, Type>> scope = new ArrayList<>();

    TypeChecker(Schema schema) {
        this.schema = schema;
    }

    /** Type of the whole rule; reports a mismatch when expected is not null. */
    Type checkRule(Expr rule, Type expected) {
        Type t = check(rule);
        if (expected != null && !Type.assignable(t, expected)) {
            error("this rule must produce " + expected.display() + ", found " + t.display(), rule.span(),
                    "this is " + t.detail(), null);
        }
        diagnostics.sort(Comparator.comparingInt((Diagnostic d) -> d.span().start()));
        return t;
    }

    List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    Map<Expr, Type> types() {
        return types;
    }

    private Type check(Expr e) {
        Type t = switch (e) {
            case NumberLit n -> Type.NUMBER;
            case TextLit s -> Type.TEXT;
            case BoolLit b -> Type.BOOL;
            case Paren p -> check(p.inner());
            case Name n -> name(n);
            case Member m -> member(m);
            case Unary u -> unary(u);
            case Binary b -> binary(b);
            case If i -> ifExpr(i);
            case Let l -> let(l);
            case Match m -> match(m);
            case Call c -> call(c);
            case ListLit l -> list(l);
        };
        types.put(e, t);
        return t;
    }

    private Type name(Name n) {
        for (int i = scope.size() - 1; i >= 0; i--) {
            if (scope.get(i).getKey().equals(n.name())) {
                return scope.get(i).getValue();
            }
        }
        Type input = schema.root().fields().get(n.name());
        if (input != null) {
            return input;
        }
        Set<String> known = new LinkedHashSet<>();
        scope.forEach(b -> known.add(b.getKey()));
        known.addAll(schema.root().fields().keySet());
        error("unknown name `" + n.name() + "`", n.span(), "not an input or a `let` name", suggestion(n.name(), known,
                "the inputs are " + String.join(", ", schema.root().fields().keySet())));
        return Type.ERROR;
    }

    private Type member(Member m) {
        Type t = check(m.target());
        if (t == Type.ERROR) {
            return Type.ERROR;
        }
        if (t instanceof RecordT r) {
            Type field = r.fields().get(m.field());
            if (field != null) {
                return field;
            }
            error("unknown field `" + m.field() + "`", m.fieldSpan(), "not a field of " + r.detail(),
                    suggestion(m.field(), r.fields().keySet(), null));
            return Type.ERROR;
        }
        error(t.display() + " has no field `" + m.field() + "`", m.target().span(), "this is " + t.detail(), null);
        return Type.ERROR;
    }

    private Type unary(Unary u) {
        Type t = check(u.operand());
        Type want = u.op() == UnaryOp.NEG ? Type.NUMBER : Type.BOOL;
        if (t != Type.ERROR && t != want) {
            error("`" + u.op().symbol + "` needs " + want.display() + ", found " + t.display(), u.operand().span(),
                    "this is " + t.detail(), null);
            return Type.ERROR;
        }
        return want;
    }

    private Type binary(Binary b) {
        Type l = check(b.left());
        Type r = check(b.right());
        return switch (b.op()) {
            case AND, OR -> {
                requireBool(b.op(), b.left(), l);
                requireBool(b.op(), b.right(), r);
                yield Type.BOOL;
            }
            case ADD -> add(b, l, r);
            case SUB, MUL, DIV, MOD -> arithmetic(b, l, r);
            case EQ, NE -> equality(b, l, r);
            case LT, LE, GT, GE -> ordering(b, l, r);
            case IN -> membership(b, l, r);
        };
    }

    private void requireBool(BinaryOp op, Expr side, Type t) {
        if (t != Type.ERROR && t != Type.BOOL) {
            error("`" + op.symbol + "` needs bool on both sides, found " + t.display(), side.span(),
                    "this is " + t.detail(), t == Type.NUMBER ? "compare it first, for example `x > 0`" : null);
        }
    }

    private Type add(Binary b, Type l, Type r) {
        if (l == Type.ERROR || r == Type.ERROR) {
            return Type.ERROR;
        }
        if (l == Type.NUMBER && r == Type.NUMBER) {
            return Type.NUMBER;
        }
        if (Type.isTextLike(l) && Type.isTextLike(r)) {
            return Type.TEXT;
        }
        error("cannot add " + l.display() + " and " + r.display(), b.span(), l.display() + " + " + r.display(),
                "`+` adds two numbers or joins two texts");
        return Type.ERROR;
    }

    private Type arithmetic(Binary b, Type l, Type r) {
        if (l == Type.ERROR || r == Type.ERROR) {
            return Type.ERROR;
        }
        if (l != Type.NUMBER || r != Type.NUMBER) {
            boolean leftBad = l != Type.NUMBER;
            Expr bad = leftBad ? b.left() : b.right();
            Type badType = leftBad ? l : r;
            error("cannot " + phrase(b.op(), l, r), bad.span(), "this is " + badType.detail(), null);
            return Type.ERROR;
        }
        if ((b.op() == BinaryOp.DIV || b.op() == BinaryOp.MOD) && unparen(b.right()) instanceof NumberLit n
                && n.value().signum() == 0) {
            error("division by zero", b.right().span(), "this is always 0", null);
        }
        return Type.NUMBER;
    }

    private static String phrase(BinaryOp op, Type l, Type r) {
        return switch (op) {
            case SUB -> "subtract " + r.display() + " from " + l.display();
            case MUL -> "multiply " + l.display() + " by " + r.display();
            case DIV -> "divide " + l.display() + " by " + r.display();
            default -> "take the remainder of " + l.display() + " divided by " + r.display();
        };
    }

    private Type equality(Binary b, Type l, Type r) {
        if (l == Type.ERROR || r == Type.ERROR) {
            return Type.BOOL;
        }
        boolean leftVariant = checkVariant(l, b.right());
        boolean rightVariant = checkVariant(r, b.left());
        Type j = Type.join(l, r);
        if (!leftVariant && !rightVariant && (j == null || j instanceof RecordT || j instanceof ListT)) {
            error("cannot compare " + l.display() + " with " + r.display(), b.span(),
                    l.display() + " " + b.op().symbol + " " + r.display(), null);
        }
        return Type.BOOL;
    }

    private Type ordering(Binary b, Type l, Type r) {
        if (l != Type.ERROR && l != Type.NUMBER) {
            error("`" + b.op().symbol + "` compares numbers, found " + l.display(), b.left().span(),
                    "this is " + l.detail(), null);
        } else if (r != Type.ERROR && r != Type.NUMBER) {
            error("`" + b.op().symbol + "` compares numbers, found " + r.display(), b.right().span(),
                    "this is " + r.detail(), null);
        }
        return Type.BOOL;
    }

    private Type membership(Binary b, Type l, Type r) {
        if (l == Type.ERROR || r == Type.ERROR) {
            return Type.BOOL;
        }
        if (!(r instanceof ListT list)) {
            error("`in` needs a list on the right, found " + r.display(), b.right().span(), "this is " + r.detail(),
                    "write a list like `[\"NL\", \"BE\"]`");
            return Type.BOOL;
        }
        boolean variantError = false;
        if (l instanceof EnumT && unparen(b.right()) instanceof ListLit items) {
            for (Expr item : items.items()) {
                variantError |= checkVariant(l, item);
            }
        }
        if (!variantError && list.element() != Type.NEVER && Type.join(l, list.element()) == null) {
            error("cannot look for " + l.display() + " in a " + r.display(), b.span(),
                    l.display() + " in " + r.display(), null);
        }
        return Type.BOOL;
    }

    /** Report a text literal that is not one of the enum's values; true when reported. */
    private boolean checkVariant(Type t, Expr other) {
        if (t instanceof EnumT en && unparen(other) instanceof TextLit lit && !en.variants().contains(lit.value())) {
            String quoted = en.variants().stream().map(Printer::quote).collect(Collectors.joining(", "));
            String near = Suggest.closest(lit.value(), en.variants());
            error(Printer.quote(lit.value()) + " is not a valid " + en.name(), lit.span(), "expected one of " + quoted,
                    near == null ? null : "did you mean " + Printer.quote(near) + "?");
            return true;
        }
        return false;
    }

    private Type ifExpr(If i) {
        Type c = check(i.condition());
        if (c != Type.ERROR && c != Type.BOOL) {
            error("the condition of `if` must be bool, found " + c.display(), i.condition().span(),
                    "this is " + c.detail(), c == Type.NUMBER ? "compare it, for example `quantity > 10`" : null);
        }
        Type then = check(i.then());
        if (i.orElse() == null) {
            error("`if` without `else` has no value when the condition is false", i.span(), "missing `else`",
                    "add an `else` branch, for example `else price`");
            return then;
        }
        Type orElse = check(i.orElse());
        Type j = Type.join(then, orElse);
        if (j == null) {
            error("the branches of `if` have different types: " + then.display() + " and " + orElse.display(),
                    i.orElse().span(), "this is " + orElse.detail(), "the `then` branch is " + then.detail());
            return Type.ERROR;
        }
        return j;
    }

    private Type let(Let l) {
        Type value = check(l.value());
        scope.add(Map.entry(l.name(), value));
        try {
            return check(l.body());
        } finally {
            scope.removeLast();
        }
    }

    private Type match(Match m) {
        Type subject = check(m.subject());
        boolean wildcard = false;
        Set<Object> seen = new HashSet<>();
        Type result = Type.NEVER;
        for (Arm arm : m.arms()) {
            if (wildcard) {
                error("unreachable arm: `_` above already matches everything", arm.pattern().span(), "never reached",
                        "remove this arm or move it above `_`");
            }
            if (arm.pattern() instanceof Wildcard) {
                wildcard = true;
            } else if (arm.pattern() instanceof LiteralPattern lp) {
                pattern(subject, lp, seen);
            }
            Type body = check(arm.body());
            Type j = Type.join(result, body);
            if (j == null) {
                error("match arms have different types: " + result.display() + " and " + body.display(),
                        arm.body().span(), "this is " + body.detail(), "earlier arms are " + result.detail());
                result = Type.ERROR;
            } else {
                result = j;
            }
        }
        if (!wildcard && subject != Type.ERROR) {
            exhaustive(m, subject, seen);
        }
        return result;
    }

    private void pattern(Type subject, LiteralPattern lp, Set<Object> seen) {
        Expr lit = lp.literal();
        Type pt = switch (lit) {
            case NumberLit n -> Type.NUMBER;
            case BoolLit b -> Type.BOOL;
            default -> Type.TEXT;
        };
        if (subject != Type.ERROR) {
            boolean variantError = checkVariant(subject, lit);
            boolean fits = subject instanceof EnumT ? pt == Type.TEXT
                    : !(subject instanceof ListT || subject instanceof RecordT) && Type.join(subject, pt) != null;
            if (!variantError && !fits) {
                error("pattern is " + pt.display() + " but the match is on " + subject.display(), lp.span(),
                        "expected " + subject.detail(), null);
            }
        }
        Object key = switch (lit) {
            case NumberLit n -> n.value().stripTrailingZeros();
            case BoolLit b -> b.value();
            case TextLit t -> t.value();
            default -> lit;
        };
        if (!seen.add(key)) {
            error("duplicate arm: " + Printer.pattern(lp) + " is already handled", lp.span(), "already handled above",
                    "remove one of the two arms");
        }
    }

    private void exhaustive(Match m, Type subject, Set<Object> seen) {
        List<String> missing = new ArrayList<>();
        if (subject == Type.BOOL) {
            for (boolean b : new boolean[] {true, false}) {
                if (!seen.contains(b)) {
                    missing.add(Boolean.toString(b));
                }
            }
        } else if (subject instanceof EnumT en) {
            en.variants().stream().filter(v -> !seen.contains(v)).map(Printer::quote).forEach(missing::add);
        } else {
            error("match on " + subject.display() + " needs a final `_ => ...` arm", m.span(), "not exhaustive",
                    "only bool and enum values can be listed completely");
            return;
        }
        if (!missing.isEmpty()) {
            error("match is not exhaustive: missing " + String.join(", ", missing), m.span(),
                    "not every case is handled", "add `" + missing.getFirst() + " => ...` or a final `_ => ...`");
        }
    }

    private Type call(Call c) {
        List<Type> args = c.args().stream().map(this::check).toList();
        Builtins.Signature sig = Builtins.ALL.get(c.function());
        if (sig == null) {
            error("unknown function `" + c.function() + "`", c.nameSpan(), "not a builtin",
                    suggestion(c.function(), Builtins.ALL.keySet(),
                            "builtins are " + String.join(", ", Builtins.ALL.keySet())));
            return Type.ERROR;
        }
        if (args.size() < sig.minArgs() || args.size() > sig.maxArgs()) {
            String expected = sig.variadic() ? "at least " + sig.minArgs() : String.valueOf(sig.minArgs());
            error("`" + c.function() + "` takes " + expected + " argument" + (sig.minArgs() == 1 ? "" : "s")
                    + ", found " + args.size(), c.span(), "wrong number of arguments", "usage: " + sig.usage());
            return sig.result();
        }
        for (int i = 0; i < args.size(); i++) {
            Type t = args.get(i);
            Builtins.Param p = sig.param(i);
            if (t != Type.ERROR && !p.accepts(t)) {
                error("argument " + (i + 1) + " of `" + c.function() + "` must be " + p.label + ", found "
                        + t.display(), c.args().get(i).span(), "this is " + t.detail(), "usage: " + sig.usage());
            }
        }
        if (c.function().equals("round") && unparen(c.args().get(1)) instanceof NumberLit digits
                && !isSmallWholeNumber(digits.value())) {
            error("`round` digits must be a whole number from 0 to 10", digits.span(), "not allowed", null);
        }
        return sig.result();
    }

    private static boolean isSmallWholeNumber(BigDecimal v) {
        return v.stripTrailingZeros().scale() <= 0 && v.signum() >= 0 && v.compareTo(BigDecimal.TEN) <= 0;
    }

    private Type list(ListLit l) {
        Type element = Type.NEVER;
        for (Expr item : l.items()) {
            Type t = check(item);
            Type j = Type.join(element, t);
            if (j == null) {
                error("list items must all have the same type: found " + element.display() + " and " + t.display(),
                        item.span(), "this is " + t.detail(), null);
                element = Type.ERROR;
            } else {
                element = j;
            }
        }
        return new ListT(element);
    }

    private static Expr unparen(Expr e) {
        Expr cur = e;
        while (cur instanceof Paren p) {
            cur = p.inner();
        }
        return cur;
    }

    private static String suggestion(String word, Collection<String> candidates, String fallback) {
        String near = Suggest.closest(word, candidates);
        return near != null ? "did you mean `" + near + "`?" : fallback;
    }

    private void error(String message, Span span, String label, String hint) {
        diagnostics.add(new Diagnostic(message, span, label, hint));
    }
}
