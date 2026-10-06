package portfolio.rules;

/** One lexical token: its kind, exact text, decoded value and span. */
record Token(Kind kind, String lexeme, Object value, Span span) {

    /** Every token kind the lexer produces. */
    enum Kind {
        NUMBER, TEXT, IDENT, WILDCARD,
        IF, THEN, ELSE, LET, MATCH, AND, OR, NOT, IN, TRUE, FALSE,
        LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE,
        COMMA, DOT, COLON, SEMICOLON, ARROW, ASSIGN,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        EQ, NE, LT, LE, GT, GE,
        EOF
    }

    /** How the token reads in an error message. */
    String describe() {
        return kind == Kind.EOF ? "end of rule" : "`" + lexeme + "`";
    }
}
