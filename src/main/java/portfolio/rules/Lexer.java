package portfolio.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import portfolio.rules.Token.Kind;

/** Turns rule text into tokens; stops with a diagnostic at the first bad character. */
final class Lexer {
    /** Longest rule accepted, in characters. */
    static final int MAX_SOURCE = 64 * 1024;
    /** Longest number literal accepted, in characters. */
    static final int MAX_NUMBER = 40;

    private static final Map<String, Kind> KEYWORDS = Map.ofEntries(
            Map.entry("if", Kind.IF), Map.entry("then", Kind.THEN), Map.entry("else", Kind.ELSE),
            Map.entry("let", Kind.LET), Map.entry("match", Kind.MATCH), Map.entry("and", Kind.AND),
            Map.entry("or", Kind.OR), Map.entry("not", Kind.NOT), Map.entry("in", Kind.IN),
            Map.entry("true", Kind.TRUE), Map.entry("false", Kind.FALSE));

    private final String src;
    private final List<Token> out = new ArrayList<>();
    private int pos;

    private Lexer(String src) {
        this.src = src;
    }

    /** All tokens of the text, ending with EOF. */
    static List<Token> lex(String src) {
        if (src.length() > MAX_SOURCE) {
            throw error("rule is too long: " + src.length() + " characters (limit " + MAX_SOURCE + ")",
                    new Span(MAX_SOURCE, src.length()), "the limit is reached here", null);
        }
        return new Lexer(src).run();
    }

    private List<Token> run() {
        while (true) {
            skipSpaceAndComments();
            if (pos >= src.length()) {
                out.add(new Token(Kind.EOF, "", null, new Span(src.length(), src.length())));
                return out;
            }
            char c = src.charAt(pos);
            if (isDigit(c)) {
                number();
            } else if (c == '"') {
                text();
            } else if (isIdentStart(c)) {
                word();
            } else {
                symbol(c);
            }
        }
    }

    private void skipSpaceAndComments() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else if (c == '#') {
                while (pos < src.length() && src.charAt(pos) != '\n') {
                    pos++;
                }
            } else {
                return;
            }
        }
    }

    private void number() {
        int start = pos;
        while (pos < src.length() && isDigit(src.charAt(pos))) {
            pos++;
        }
        if (pos + 1 < src.length() && src.charAt(pos) == '.' && isDigit(src.charAt(pos + 1))) {
            pos++;
            while (pos < src.length() && isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        if (pos < src.length() && isIdentPart(src.charAt(pos))) {
            int end = pos;
            while (end < src.length() && isIdentPart(src.charAt(end))) {
                end++;
            }
            throw error("invalid number `" + src.substring(start, end) + "`", new Span(start, end),
                    "not a number", "numbers are digits with an optional fraction, like 12 or 0.95");
        }
        if (pos - start > MAX_NUMBER) {
            throw error("number is too long (limit " + MAX_NUMBER + " characters)", new Span(start, pos),
                    "here", null);
        }
        add(Kind.NUMBER, start, new BigDecimal(src.substring(start, pos)));
    }

    private void text() {
        int start = pos++;
        StringBuilder value = new StringBuilder();
        while (true) {
            if (pos >= src.length() || src.charAt(pos) == '\n' || src.charAt(pos) == '\r') {
                throw error("unterminated text", new Span(start, pos), "this text has no closing `\"`",
                        "text must end with `\"` on the same line");
            }
            char c = src.charAt(pos);
            if (c == '"') {
                pos++;
                add(Kind.TEXT, start, value.toString());
                return;
            }
            if (c == '\\') {
                escape(value);
            } else {
                value.append(c);
                pos++;
            }
        }
    }

    private void escape(StringBuilder value) {
        int start = pos;
        if (pos + 1 >= src.length()) {
            throw error("unterminated text", new Span(start, src.length()), "escape at end of rule", null);
        }
        char e = src.charAt(pos + 1);
        pos += 2;
        switch (e) {
            case '"' -> value.append('"');
            case '\\' -> value.append('\\');
            case 'n' -> value.append('\n');
            case 't' -> value.append('\t');
            case 'r' -> value.append('\r');
            case 'u' -> value.appendCodePoint(unicodeEscape(start));
            default -> throw error("unknown escape `\\" + e + "`", new Span(start, pos), "not an escape",
                    "use \\\" \\\\ \\n \\t \\r or \\u{1F600}");
        }
    }

    private int unicodeEscape(int start) {
        String usage = "write it as \\u{1F600}";
        if (pos >= src.length() || src.charAt(pos) != '{') {
            throw error("bad unicode escape", new Span(start, pos), usage, null);
        }
        int close = src.indexOf('}', pos);
        int digits = close - pos - 1;
        if (close < 0 || digits < 1 || digits > 6) {
            throw error("bad unicode escape", new Span(start, pos + 1), usage, null);
        }
        String hex = src.substring(pos + 1, close);
        pos = close + 1;
        int cp;
        try {
            cp = Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw error("bad unicode escape", new Span(start, pos), "not a hexadecimal number", null);
        }
        if (cp < 0 || !Character.isValidCodePoint(cp) || (cp >= 0xD800 && cp <= 0xDFFF)) {
            throw error("bad unicode escape", new Span(start, pos), "not a valid character", null);
        }
        return cp;
    }

    private void word() {
        int start = pos;
        while (pos < src.length() && isIdentPart(src.charAt(pos))) {
            pos++;
        }
        String w = src.substring(start, pos);
        if (w.equals("_")) {
            add(Kind.WILDCARD, start, null);
        } else {
            add(KEYWORDS.getOrDefault(w, Kind.IDENT), start, w);
        }
    }

    private void symbol(char c) {
        int start = pos;
        char next = pos + 1 < src.length() ? src.charAt(pos + 1) : '\0';
        Kind kind = switch (c) {
            case '(' -> Kind.LPAREN;
            case ')' -> Kind.RPAREN;
            case '[' -> Kind.LBRACKET;
            case ']' -> Kind.RBRACKET;
            case '{' -> Kind.LBRACE;
            case '}' -> Kind.RBRACE;
            case ',' -> Kind.COMMA;
            case '.' -> Kind.DOT;
            case ':' -> Kind.COLON;
            case ';' -> Kind.SEMICOLON;
            case '+' -> Kind.PLUS;
            case '-' -> Kind.MINUS;
            case '*' -> Kind.STAR;
            case '/' -> Kind.SLASH;
            case '%' -> Kind.PERCENT;
            case '=' -> next == '=' ? Kind.EQ : next == '>' ? Kind.ARROW : Kind.ASSIGN;
            case '!' -> next == '=' ? Kind.NE : null;
            case '<' -> next == '=' ? Kind.LE : Kind.LT;
            case '>' -> next == '=' ? Kind.GE : Kind.GT;
            default -> null;
        };
        if (kind == null) {
            int cp = src.codePointAt(pos);
            String shown = Character.isISOControl(cp) || Character.isWhitespace(cp)
                    ? String.format("U+%04X", cp) : new String(Character.toChars(cp));
            String hint = switch (c) {
                case '!' -> "use `not` for negation";
                case '&' -> "use `and`";
                case '|' -> "use `or`";
                default -> null;
            };
            throw error("unexpected character `" + shown + "`", new Span(pos, pos + Character.charCount(cp)),
                    "not part of the rule language", hint);
        }
        boolean twoChars = switch (kind) {
            case EQ, ARROW, NE, LE, GE -> true;
            default -> false;
        };
        pos += twoChars ? 2 : 1;
        add(kind, start, null);
    }

    private void add(Kind kind, int start, Object value) {
        out.add(new Token(kind, src.substring(start, pos), value, new Span(start, pos)));
    }

    private static RuleSyntaxException error(String message, Span span, String label, String hint) {
        return new RuleSyntaxException(new Diagnostic(message, span, label, hint));
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }
}
