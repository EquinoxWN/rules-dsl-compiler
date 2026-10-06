package portfolio.rules;

/** One problem in a rule, pointing at the code that causes it. */
public record Diagnostic(String message, Span span, String label, String hint) {

    /** Diagnostic without a hint. */
    public static Diagnostic error(String message, Span span, String label) {
        return new Diagnostic(message, span, label, null);
    }

    /** Same diagnostic with a hint line. */
    public Diagnostic withHint(String newHint) {
        return new Diagnostic(message, span, label, newHint);
    }

    /** Compiler-style text with the offending code underlined. */
    public String render(Source source) {
        String text = source.text();
        int start = Math.min(span.start(), text.length());
        int line = source.line(start);
        int column = source.column(start);
        int lineStart = source.lineStart(start);
        int lineEnd = source.lineEnd(start);
        String code = text.substring(lineStart, lineEnd).replace('\t', ' ');
        int endOnLine = Math.max(start, Math.min(span.end(), lineEnd));
        int width = Math.max(1, text.codePointCount(start, endOnLine));
        String gutter = " ".repeat(String.valueOf(line).length());
        StringBuilder out = new StringBuilder()
                .append("error: ").append(message).append('\n')
                .append(gutter).append("--> ").append(source.name()).append(':')
                .append(line).append(':').append(column).append('\n')
                .append(gutter).append(" |\n")
                .append(line).append(" | ").append(code).append('\n')
                .append(gutter).append(" | ").append(" ".repeat(column - 1)).append("^".repeat(width));
        if (label != null && !label.isEmpty()) {
            out.append(' ').append(label);
        }
        out.append('\n');
        if (hint != null && !hint.isEmpty()) {
            out.append(gutter).append(" = hint: ").append(hint).append('\n');
        }
        return out.toString();
    }
}
