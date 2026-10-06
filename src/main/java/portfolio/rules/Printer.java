package portfolio.rules;

import java.util.List;
import java.util.stream.Collectors;
import portfolio.rules.Ast.Arm;
import portfolio.rules.Ast.Binary;
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

/**
 * Prints a tree back to source with every compound expression parenthesised, so the output
 * shows exactly how the parser grouped the rule and parses back to the same tree.
 */
public final class Printer {
    private Printer() {
    }

    /** Canonical, fully parenthesised source for a tree. */
    public static String print(Expr e) {
        return switch (e) {
            case NumberLit n -> n.value().toPlainString();
            case TextLit t -> quote(t.value());
            case BoolLit b -> Boolean.toString(b.value());
            case Name n -> n.name();
            case Member m -> print(m.target()) + "." + m.field();
            case Call c -> c.function() + "(" + join(c.args()) + ")";
            case Unary u -> "(" + u.op().symbol + (u.op() == UnaryOp.NOT ? " " : "") + print(u.operand()) + ")";
            case Binary b -> "(" + print(b.left()) + " " + b.op().symbol + " " + print(b.right()) + ")";
            case If i -> "(if " + print(i.condition()) + " then " + print(i.then())
                    + (i.orElse() == null ? "" : " else " + print(i.orElse())) + ")";
            case Let l -> "(let " + l.name() + " = " + print(l.value()) + "; " + print(l.body()) + ")";
            case Match m -> "(match " + print(m.subject()) + " { "
                    + m.arms().stream().map(Printer::arm).collect(Collectors.joining(", ")) + " })";
            case ListLit l -> "[" + join(l.items()) + "]";
            case Paren p -> print(p.inner());
        };
    }

    private static String join(List<Expr> items) {
        return items.stream().map(Printer::print).collect(Collectors.joining(", "));
    }

    private static String arm(Arm a) {
        return pattern(a.pattern()) + " => " + print(a.body());
    }

    /** Source form of a match pattern. */
    static String pattern(Pattern p) {
        return switch (p) {
            case Wildcard w -> "_";
            case LiteralPattern l -> print(l.literal());
        };
    }

    /** Text literal with escapes, readable back by the lexer. */
    public static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        s.codePoints().forEach(cp -> {
            switch (cp) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                default -> {
                    if (Character.isISOControl(cp)) {
                        out.append("\\u{").append(Integer.toHexString(cp)).append('}');
                    } else {
                        out.appendCodePoint(cp);
                    }
                }
            }
        });
        return out.append('"').toString();
    }
}
