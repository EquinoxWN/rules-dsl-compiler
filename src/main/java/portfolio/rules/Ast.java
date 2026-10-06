package portfolio.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Syntax tree of a rule; every node keeps the span of the code it came from. */
public final class Ast {
    private Ast() {
    }

    /** Any expression; a whole rule is one expression. */
    public sealed interface Expr permits NumberLit, TextLit, BoolLit, Name, Member, Call, Unary, Binary, If, Let,
            Match, ListLit, Paren {
        /** Code this node was parsed from. */
        Span span();
    }

    /** Decimal number such as {@code 0.95}. */
    public record NumberLit(BigDecimal value, Span span) implements Expr {
    }

    /** Text literal such as {@code "gold"}. */
    public record TextLit(String value, Span span) implements Expr {
    }

    /** {@code true} or {@code false}. */
    public record BoolLit(boolean value, Span span) implements Expr {
    }

    /** A let-bound name or a top-level input such as {@code price}. */
    public record Name(String name, Span span) implements Expr {
    }

    /** Field access such as {@code customer.tier}. */
    public record Member(Expr target, String field, Span fieldSpan, Span span) implements Expr {
    }

    /** Builtin call such as {@code min(price, 100)}. */
    public record Call(String function, Span nameSpan, List<Expr> args, Span span) implements Expr {
        public Call {
            args = List.copyOf(args);
        }
    }

    /** Prefix operator applied to one operand. */
    public record Unary(UnaryOp op, Expr operand, Span span) implements Expr {
    }

    /** Infix operator applied to two operands. */
    public record Binary(BinaryOp op, Expr left, Expr right, Span span) implements Expr {
    }

    /** {@code if c then a else b}; {@code orElse} is null when the else branch is missing. */
    public record If(Expr condition, Expr then, Expr orElse, Span span) implements Expr {
    }

    /** {@code let name = value; body}. */
    public record Let(String name, Span nameSpan, Expr value, Expr body, Span span) implements Expr {
    }

    /** {@code match subject { pattern => body, ... }}. */
    public record Match(Expr subject, List<Arm> arms, Span span) implements Expr {
        public Match {
            arms = List.copyOf(arms);
        }
    }

    /** One arm of a match. */
    public record Arm(Pattern pattern, Expr body, Span span) {
    }

    /** What a match arm compares against. */
    public sealed interface Pattern permits LiteralPattern, Wildcard {
        /** Code this pattern was parsed from. */
        Span span();
    }

    /** A number, text or bool literal pattern; numbers may be negative. */
    public record LiteralPattern(Expr literal, Span span) implements Pattern {
    }

    /** {@code _}, which matches anything. */
    public record Wildcard(Span span) implements Pattern {
    }

    /** List literal such as {@code ["NL", "BE"]}. */
    public record ListLit(List<Expr> items, Span span) implements Expr {
        public ListLit {
            items = List.copyOf(items);
        }
    }

    /** Parenthesised expression; kept so spans include the parentheses. */
    public record Paren(Expr inner, Span span) implements Expr {
    }

    /** Prefix operators. */
    public enum UnaryOp {
        NEG("-"), NOT("not");

        /** Source spelling. */
        public final String symbol;

        UnaryOp(String symbol) {
            this.symbol = symbol;
        }
    }

    /** Infix operators. */
    public enum BinaryOp {
        OR("or"), AND("and"),
        EQ("=="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">="), IN("in"),
        ADD("+"), SUB("-"), MUL("*"), DIV("/"), MOD("%");

        /** Source spelling. */
        public final String symbol;

        BinaryOp(String symbol) {
            this.symbol = symbol;
        }
    }

    /** Direct child expressions of a node, in source order (match patterns excluded). */
    public static List<Expr> children(Expr e) {
        return switch (e) {
            case NumberLit n -> List.of();
            case TextLit t -> List.of();
            case BoolLit b -> List.of();
            case Name n -> List.of();
            case Member m -> List.of(m.target());
            case Call c -> c.args();
            case Unary u -> List.of(u.operand());
            case Binary b -> List.of(b.left(), b.right());
            case If i -> i.orElse() == null ? List.of(i.condition(), i.then())
                    : List.of(i.condition(), i.then(), i.orElse());
            case Let l -> List.of(l.value(), l.body());
            case Match m -> {
                List<Expr> out = new ArrayList<>();
                out.add(m.subject());
                m.arms().forEach(a -> out.add(a.body()));
                yield out;
            }
            case ListLit l -> l.items();
            case Paren p -> List.of(p.inner());
        };
    }

    /** Every node in the tree, parents before children. */
    public static List<Expr> all(Expr root) {
        List<Expr> out = new ArrayList<>();
        List<Expr> stack = new ArrayList<>(List.of(root));
        while (!stack.isEmpty()) {
            Expr e = stack.removeLast();
            out.add(e);
            List<Expr> kids = children(e);
            for (int i = kids.size() - 1; i >= 0; i--) {
                stack.add(kids.get(i));
            }
        }
        return out;
    }
}
