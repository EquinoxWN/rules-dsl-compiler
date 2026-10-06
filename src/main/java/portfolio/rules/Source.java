package portfolio.rules;

import java.util.Objects;

/** A rule's text plus the name shown in error messages. */
public record Source(String name, String text) {
    public Source {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(text, "text");
    }

    /** Source named "rule". */
    public static Source of(String text) {
        return new Source("rule", text);
    }

    /** 1-based line number of an offset. */
    public int line(int offset) {
        int line = 1;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** 1-based column of an offset, counted in code points. */
    public int column(int offset) {
        int start = lineStart(offset);
        return text.codePointCount(start, Math.min(offset, text.length())) + 1;
    }

    /** Offset where the line holding the offset begins. */
    int lineStart(int offset) {
        int i = Math.min(offset, text.length());
        while (i > 0 && text.charAt(i - 1) != '\n') {
            i--;
        }
        return i;
    }

    /** Offset where the line holding the offset ends, before any line break. */
    int lineEnd(int offset) {
        int i = Math.min(offset, text.length());
        while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
            i++;
        }
        return i;
    }
}
