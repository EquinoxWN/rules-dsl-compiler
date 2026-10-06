package portfolio.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
import portfolio.rules.Ast.Pattern;
import portfolio.rules.Ast.TextLit;
import portfolio.rules.Ast.Unary;
import portfolio.rules.Ast.UnaryOp;
import portfolio.rules.Ast.Wildcard;
import portfolio.rules.Token.Kind;

/**
 * Pratt parser: each operator has a left and right binding power, so precedence and
 * associativity live in one table instead of one grammar rule per level.
 */
final class Parser {
    /** Deepest nesting accepted, so hostile input cannot overflow the stack. */
    static final int MAX_DEPTH = 200;

    private static final int POSTFIX_BP = 15;
    private static final int NEG_BP = 13;
    private static final int NOT_BP = 5;

    private final List<Token> tokens;
    private final Map<Expr, Integer> heights = new IdentityHashMap<>();
    private int pos;
    private int depth;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /** Parse a whole rule or throw at the first syntax error. */
    static Expr parse(String source) {
        Parser p = new Parser(Lexer.lex(source));
        Expr e = p.expr(0);
        Token end = p.peek();
        if (end.kind() != Kind.EOF) {
            throw error("expected end of rule, found " + end.describe(), end.span(), "unexpected here",
                    end.kind() == Kind.RPAREN ? "this `)` has no matching `(`" : null);
        }
        return e;
    }

    private Expr expr(int minBp) {
        if (++depth > MAX_DEPTH) {
            throw error("rule is nested too deeply (limit " + MAX_DEPTH + " levels)", peek().span(),
                    "too deep here", "split the rule into smaller `let` steps");
        }
        try {
            Expr lhs = prefix();
            while (true) {
                Token op = peek();
                if (op.kind() == Kind.DOT || op.kind() == Kind.LPAREN) {
                    if (POSTFIX_BP < minBp) {
                        break;
                    }
                    lhs = postfix(lhs);
                    continue;
                }
                BinaryOp bop = binaryOp(op.kind());
                if (bop == null) {
                    break;
                }
                int[] bp = bindingPower(bop);
                if (bp[0] < minBp) {
                    break;
                }
                advance();
                Expr rhs = expr(bp[1]);
                lhs = node(new Binary(bop, lhs, rhs, lhs.span().to(rhs.span())), lhs, rhs);
                BinaryOp next = binaryOp(peek().kind());
                if (isComparison(bop) && next != null && isComparison(next)) {
                    throw error("comparisons cannot be chained", peek().span(), "second comparison",
                            "join them with `and`, for example `a < b and b < c`");
                }
            }
            return lhs;
        } finally {
            depth--;
        }
    }

    private Expr prefix() {
        Token t = advance();
        return switch (t.kind()) {
            case NUMBER -> node(new NumberLit((BigDecimal) t.value(), t.span()));
            case TEXT -> node(new TextLit((String) t.value(), t.span()));
            case TRUE -> node(new BoolLit(true, t.span()));
            case FALSE -> node(new BoolLit(false, t.span()));
            case IDENT -> node(new Name((String) t.value(), t.span()));
            case LPAREN -> {
                Expr inner = expr(0);
                Token close = expect(Kind.RPAREN, "`)`", t);
                yield node(new Paren(inner, t.span().to(close.span())), inner);
            }
            case LBRACKET -> list(t);
            case MINUS -> {
                Expr operand = expr(NEG_BP);
                yield node(new Unary(UnaryOp.NEG, operand, t.span().to(operand.span())), operand);
            }
            case NOT -> {
                Expr operand = expr(NOT_BP);
                yield node(new Unary(UnaryOp.NOT, operand, t.span().to(operand.span())), operand);
            }
            case IF -> ifExpr(t);
            case LET -> letExpr(t);
            case MATCH -> matchExpr(t);
            case WILDCARD -> throw error("`_` can only be used as a match pattern", t.span(), "not allowed here",
                    null);
            case EOF -> throw error("expected an expression, found end of rule", t.span(), "rule ends here", null);
            default -> throw error("expected an expression, found " + t.describe(), t.span(),
                    "expected a value here", null);
        };
    }

    private Expr postfix(Expr lhs) {
        Token t = advance();
        if (t.kind() == Kind.DOT) {
            Token field = peek();
            if (field.kind() != Kind.IDENT) {
                throw error("expected a field name after `.`, found " + field.describe(), field.span(),
                        "expected a name", null);
            }
            advance();
            return node(new Member(lhs, (String) field.value(), field.span(), lhs.span().to(field.span())), lhs);
        }
        if (!(lhs instanceof Name name)) {
            throw error("only builtin functions can be called", lhs.span(), "this is not a function name",
                    "call a builtin by name, like `min(price, 100)`");
        }
        List<Expr> args = new ArrayList<>();
        if (peek().kind() != Kind.RPAREN) {
            do {
                args.add(expr(0));
            } while (accept(Kind.COMMA));
        }
        Token close = expect(Kind.RPAREN, "`)` or `,`", t);
        return node(new Call(name.name(), name.span(), args, lhs.span().to(close.span())),
                args.toArray(Expr[]::new));
    }

    private Expr list(Token open) {
        List<Expr> items = new ArrayList<>();
        while (peek().kind() != Kind.RBRACKET) {
            items.add(expr(0));
            if (!accept(Kind.COMMA)) {
                break;
            }
        }
        Token close = expect(Kind.RBRACKET, "`]` or `,`", open);
        return node(new ListLit(items, open.span().to(close.span())), items.toArray(Expr[]::new));
    }

    private Expr ifExpr(Token start) {
        Expr condition = expr(0);
        expect(Kind.THEN, "`then`", start);
        Expr then = expr(0);
        if (accept(Kind.ELSE)) {
            Expr orElse = expr(0);
            return node(new If(condition, then, orElse, start.span().to(orElse.span())), condition, then, orElse);
        }
        return node(new If(condition, then, null, start.span().to(then.span())), condition, then);
    }

    private Expr letExpr(Token start) {
        Token name = peek();
        if (name.kind() != Kind.IDENT) {
            throw error("expected a name after `let`, found " + name.describe(), name.span(), "expected a name",
                    null);
        }
        advance();
        expect(Kind.ASSIGN, "`=`", start);
        Expr value = expr(0);
        expect(Kind.SEMICOLON, "`;`", start);
        Expr body = expr(0);
        return node(new Let((String) name.value(), name.span(), value, body, start.span().to(body.span())),
                value, body);
    }

    private Expr matchExpr(Token start) {
        Expr subject = expr(0);
        expect(Kind.LBRACE, "`{`", start);
        List<Arm> arms = new ArrayList<>();
        List<Expr> kids = new ArrayList<>(List.of(subject));
        while (peek().kind() != Kind.RBRACE) {
            Pattern pattern = pattern();
            expect(Kind.ARROW, "`=>`", start);
            Expr body = expr(0);
            arms.add(new Arm(pattern, body, pattern.span().to(body.span())));
            kids.add(body);
            if (!accept(Kind.COMMA)) {
                break;
            }
        }
        Token close = expect(Kind.RBRACE, "`}` or `,`", start);
        if (arms.isEmpty()) {
            throw error("match needs at least one arm", start.span().to(close.span()), "no arms",
                    "add arms like `\"gold\" => 0.9, _ => 1`");
        }
        return node(new Match(subject, arms, start.span().to(close.span())), kids.toArray(Expr[]::new));
    }

    private Pattern pattern() {
        Token t = advance();
        return switch (t.kind()) {
            case WILDCARD -> new Wildcard(t.span());
            case NUMBER -> new LiteralPattern(new NumberLit((BigDecimal) t.value(), t.span()), t.span());
            case TEXT -> new LiteralPattern(new TextLit((String) t.value(), t.span()), t.span());
            case TRUE, FALSE -> new LiteralPattern(new BoolLit(t.kind() == Kind.TRUE, t.span()), t.span());
            case MINUS -> {
                Token n = advance();
                if (n.kind() != Kind.NUMBER) {
                    throw error("expected a number after `-` in a pattern", n.span(), "expected a number", null);
                }
                Span span = t.span().to(n.span());
                yield new LiteralPattern(new NumberLit(((BigDecimal) n.value()).negate(), span), span);
            }
            default -> throw error("expected a pattern, found " + t.describe(), t.span(), "expected a pattern",
                    "patterns are literals like `\"gold\"`, `10`, `true`, or `_` for anything else");
        };
    }

    /** Record a node's height and refuse trees deeper than the limit. */
    private Expr node(Expr e, Expr... kids) {
        int h = 1;
        for (Expr k : kids) {
            h = Math.max(h, heights.getOrDefault(k, 1) + 1);
        }
        if (h > MAX_DEPTH) {
            throw error("rule is nested too deeply (limit " + MAX_DEPTH + " levels)", e.span(), "too deep here",
                    "split the rule into smaller `let` steps");
        }
        heights.put(e, h);
        return e;
    }

    private static BinaryOp binaryOp(Kind k) {
        return switch (k) {
            case OR -> BinaryOp.OR;
            case AND -> BinaryOp.AND;
            case EQ -> BinaryOp.EQ;
            case NE -> BinaryOp.NE;
            case LT -> BinaryOp.LT;
            case LE -> BinaryOp.LE;
            case GT -> BinaryOp.GT;
            case GE -> BinaryOp.GE;
            case IN -> BinaryOp.IN;
            case PLUS -> BinaryOp.ADD;
            case MINUS -> BinaryOp.SUB;
            case STAR -> BinaryOp.MUL;
            case SLASH -> BinaryOp.DIV;
            case PERCENT -> BinaryOp.MOD;
            default -> null;
        };
    }

    /** Left and right binding power; left below right makes the operator left-associative. */
    private static int[] bindingPower(BinaryOp op) {
        return switch (op) {
            case OR -> new int[] {1, 2};
            case AND -> new int[] {3, 4};
            case EQ, NE, LT, LE, GT, GE, IN -> new int[] {7, 8};
            case ADD, SUB -> new int[] {9, 10};
            case MUL, DIV, MOD -> new int[] {11, 12};
        };
    }

    private static boolean isComparison(BinaryOp op) {
        return switch (op) {
            case EQ, NE, LT, LE, GT, GE, IN -> true;
            default -> false;
        };
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token advance() {
        Token t = tokens.get(pos);
        if (t.kind() != Kind.EOF) {
            pos++;
        }
        return t;
    }

    private boolean accept(Kind k) {
        if (peek().kind() == k) {
            advance();
            return true;
        }
        return false;
    }

    private Token expect(Kind k, String what, Token opener) {
        Token t = peek();
        if (t.kind() != k) {
            String hint = switch (opener.kind()) {
                case LPAREN, LBRACKET -> "an earlier " + opener.describe() + " is still open";
                default -> null;
            };
            throw error("expected " + what + ", found " + t.describe(), t.span(), "expected " + what + " here", hint);
        }
        return advance();
    }

    private static RuleSyntaxException error(String message, Span span, String label, String hint) {
        return new RuleSyntaxException(new Diagnostic(message, span, label, hint));
    }
}
