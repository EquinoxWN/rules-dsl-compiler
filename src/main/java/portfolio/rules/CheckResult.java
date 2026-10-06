package portfolio.rules;

import java.util.List;
import java.util.Map;
import portfolio.rules.Ast.Expr;

/**
 * Outcome of checking a rule: the tree (null after a syntax error), its type and every
 * diagnostic. The rule is safe to hand to the compiler only when {@link #ok()} is true.
 */
public record CheckResult(Expr ast, Type type, List<Diagnostic> diagnostics, Map<Expr, Type> types) {
    public CheckResult {
        diagnostics = List.copyOf(diagnostics);
    }

    /** True when the rule has no errors. */
    public boolean ok() {
        return diagnostics.isEmpty();
    }

    /** Inferred type of one node of the tree. */
    public Type typeOf(Expr node) {
        return types.get(node);
    }
}
