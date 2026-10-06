package portfolio.rules;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import portfolio.rules.Ast.Expr;

/** Entry point for host applications: parse and type-check a rule against a schema. */
public final class Rules {
    private Rules() {
    }

    /** Parse only; throws RuleSyntaxException at the first syntax error. */
    public static Expr parse(String source) {
        return Parser.parse(Objects.requireNonNull(source, "source"));
    }

    /** Check a rule; never throws for any input text. */
    public static CheckResult check(Source source, Schema schema, Type expected) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(schema, "schema");
        Expr ast;
        try {
            ast = Parser.parse(source.text());
        } catch (RuleSyntaxException e) {
            return new CheckResult(null, null, List.of(e.diagnostic()), Map.of());
        }
        TypeChecker checker = new TypeChecker(schema);
        Type type = checker.checkRule(ast, expected);
        return new CheckResult(ast, type, checker.diagnostics(), checker.types());
    }

    /** Check rule text named "rule". */
    public static CheckResult check(String source, Schema schema, Type expected) {
        return check(Source.of(source), schema, expected);
    }

    /** All diagnostics rendered with underlined code, separated by blank lines. */
    public static String render(Source source, List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(d -> d.render(source)).collect(Collectors.joining("\n"));
    }
}
