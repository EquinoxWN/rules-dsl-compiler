package portfolio.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import portfolio.rules.Token.Kind;

class LexerTest {

    private static List<Kind> kinds(String src) {
        return Lexer.lex(src).stream().map(Token::kind).toList();
    }

    @Test
    void tokenizesTheReadmeExample() {
        assertEquals(List.of(Kind.IF, Kind.IDENT, Kind.DOT, Kind.IDENT, Kind.EQ, Kind.TEXT, Kind.THEN, Kind.IDENT,
                Kind.STAR, Kind.NUMBER, Kind.ELSE, Kind.IDENT, Kind.EOF),
                kinds("if customer.tier == \"gold\" then price * 0.9 else price"));
    }

    @Test
    void everyTokenSpanCoversExactlyItsText() {
        String src = "let x = min(price, 10); # comment\n  x >= 2.50 and \"a\\\"b\" != y";
        for (Token t : Lexer.lex(src)) {
            assertEquals(t.lexeme(), t.span().of(src), t.kind().name());
        }
    }

    @Test
    void twoCharacterOperatorsWinOverOneCharacter() {
        assertEquals(List.of(Kind.EQ, Kind.NE, Kind.LE, Kind.GE, Kind.ARROW, Kind.ASSIGN, Kind.LT, Kind.GT, Kind.EOF),
                kinds("== != <= >= => = < >"));
    }

    @Test
    void numbersAreExactDecimals() {
        Token t = Lexer.lex("0.10").getFirst();
        assertEquals(new BigDecimal("0.10"), t.value());
    }

    @Test
    void escapesAreDecoded() {
        Token t = Lexer.lex("\"q\\\"b\\\\s\\n\\t\\u{1F600}é\"").getFirst();
        assertEquals("q\"b\\s\n\t😀é", t.value());
    }

    @Test
    void commentsAndWhitespaceAreSkipped() {
        assertEquals(List.of(Kind.NUMBER, Kind.PLUS, Kind.NUMBER, Kind.EOF), kinds("# total\n1 +\t\r\n 2 # done"));
    }

    @Test
    void keywordsAreReservedButLongerWordsAreNames() {
        assertEquals(List.of(Kind.IN, Kind.IDENT, Kind.IDENT, Kind.WILDCARD, Kind.IDENT, Kind.EOF),
                kinds("in inside iffy _ _x"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            "abc                 | unterminated text
            "a\\q"               | unknown escape
            "\\u{110000}"        | bad unicode escape
            "\\u{zz}"            | bad unicode escape
            "\\u12"              | bad unicode escape
            12abc                | invalid number `12abc`
            price & 1            | unexpected character `&`
            price @ 1            | unexpected character `@`
            ! price              | unexpected character `!`
            """)
    void badInputStopsWithAClearMessage(String src, String message) {
        RuleSyntaxException e = assertThrows(RuleSyntaxException.class, () -> Lexer.lex(src));
        assertTrue(e.getMessage().startsWith(message), e.getMessage());
        Span s = e.diagnostic().span();
        assertTrue(s.start() >= 0 && s.end() <= src.length(), s.toString());
    }

    @Test
    void lineBreakInsideTextIsAnError() {
        RuleSyntaxException e = assertThrows(RuleSyntaxException.class, () -> Lexer.lex("\"ab\ncd\""));
        assertEquals("unterminated text", e.getMessage());
        assertEquals(new Span(0, 3), e.diagnostic().span());
    }

    @Test
    void bangSuggestsNot() {
        RuleSyntaxException e = assertThrows(RuleSyntaxException.class, () -> Lexer.lex("!a"));
        assertEquals("use `not` for negation", e.diagnostic().hint());
    }

    @Test
    void oversizedRulesAndNumbersAreRefused() {
        assertTrue(assertThrows(RuleSyntaxException.class, () -> Lexer.lex("1".repeat(Lexer.MAX_SOURCE + 1)))
                .getMessage().startsWith("rule is too long"));
        assertTrue(assertThrows(RuleSyntaxException.class, () -> Lexer.lex("9".repeat(41)))
                .getMessage().startsWith("number is too long"));
    }
}
