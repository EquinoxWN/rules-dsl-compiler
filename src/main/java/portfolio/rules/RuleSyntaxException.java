package portfolio.rules;

/** Raised by the lexer and parser at the first syntax error. */
public final class RuleSyntaxException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The diagnostic describing the error; records are serializable here. */
    private final transient Diagnostic diagnostic;

    public RuleSyntaxException(Diagnostic diagnostic) {
        super(diagnostic.message());
        this.diagnostic = diagnostic;
    }

    /** The error with its source span. */
    public Diagnostic diagnostic() {
        return diagnostic;
    }
}
