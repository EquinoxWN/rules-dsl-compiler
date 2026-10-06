package portfolio.rules;

/** Half-open range {@code [start, end)} of character offsets in the rule source. */
public record Span(int start, int end) {
    public Span {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("bad span " + start + ".." + end);
        }
    }

    /** Smallest span covering both spans. */
    public Span to(Span other) {
        return new Span(Math.min(start, other.start), Math.max(end, other.end));
    }

    /** Text this span covers. */
    public String of(String text) {
        return text.substring(start, end);
    }
}
