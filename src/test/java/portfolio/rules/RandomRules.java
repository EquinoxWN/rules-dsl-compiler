package portfolio.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
import portfolio.rules.Ast.Pattern;
import portfolio.rules.Ast.TextLit;
import portfolio.rules.Ast.Unary;
import portfolio.rules.Ast.UnaryOp;
import portfolio.rules.Ast.Wildcard;

/** Random syntax trees (any shape, not necessarily well-typed) for property tests. */
final class RandomRules {
    private static final Span NO_SPAN = new Span(0, 0);
    private static final String[] NAMES = {"price", "quantity", "customer", "x", "rate_2", "Tier"};
    private static final String[] FIELDS = {"tier", "age", "country", "a1"};
    private static final String[] FUNCTIONS = {"min", "max", "abs", "len", "lower", "nope"};
    private static final String[] TEXT_PARTS = {"gold", "a b", "\"", "\\", "\n", "\t", "é", "😀", "\u0001", "#", ""};

    private final Random random;

    RandomRules(long seed) {
        this.random = new Random(seed);
    }

    /** A random expression of at most the given depth. */
    Expr expr(int depth) {
        int kind = depth <= 0 ? random.nextInt(4) : random.nextInt(14);
        return switch (kind) {
            case 0 -> number();
            case 1 -> text();
            case 2 -> new BoolLit(random.nextBoolean(), NO_SPAN);
            case 3 -> new Name(pick(NAMES), NO_SPAN);
            case 4 -> new Member(expr(depth - 1), pick(FIELDS), NO_SPAN, NO_SPAN);
            case 5 -> new Call(pick(FUNCTIONS), NO_SPAN, items(depth, 3), NO_SPAN);
            case 6 -> new Unary(random.nextBoolean() ? UnaryOp.NEG : UnaryOp.NOT, expr(depth - 1), NO_SPAN);
            case 7, 8, 9 -> new Binary(pick(BinaryOp.values()), expr(depth - 1), expr(depth - 1), NO_SPAN);
            case 10 -> new If(expr(depth - 1), expr(depth - 1), random.nextBoolean() ? expr(depth - 1) : null,
                    NO_SPAN);
            case 11 -> new Let(pick(NAMES), NO_SPAN, expr(depth - 1), expr(depth - 1), NO_SPAN);
            case 12 -> match(depth);
            default -> new ListLit(items(depth, 3), NO_SPAN);
        };
    }

    private Expr number() {
        return new NumberLit(BigDecimal.valueOf(random.nextInt(100_000), random.nextInt(4)), NO_SPAN);
    }

    private Expr text() {
        StringBuilder sb = new StringBuilder();
        for (int i = random.nextInt(4); i > 0; i--) {
            sb.append(pick(TEXT_PARTS));
        }
        return new TextLit(sb.toString(), NO_SPAN);
    }

    private List<Expr> items(int depth, int max) {
        List<Expr> out = new ArrayList<>();
        for (int i = random.nextInt(max + 1); i > 0; i--) {
            out.add(expr(depth - 1));
        }
        return out;
    }

    private Expr match(int depth) {
        List<Arm> arms = new ArrayList<>();
        for (int i = 1 + random.nextInt(3); i > 0; i--) {
            Pattern p = switch (random.nextInt(4)) {
                case 0 -> new Wildcard(NO_SPAN);
                case 1 -> new LiteralPattern(text(), NO_SPAN);
                case 2 -> new LiteralPattern(new BoolLit(random.nextBoolean(), NO_SPAN), NO_SPAN);
                default -> new LiteralPattern(new NumberLit(BigDecimal.valueOf(random.nextInt(201) - 100), NO_SPAN),
                        NO_SPAN);
            };
            arms.add(new Arm(p, expr(depth - 1), NO_SPAN));
        }
        return new Match(expr(depth - 1), arms, NO_SPAN);
    }

    private <T> T pick(T[] options) {
        return options[random.nextInt(options.length)];
    }
}
