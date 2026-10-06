package portfolio.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import portfolio.rules.Ast.Binary;
import portfolio.rules.Ast.BinaryOp;
import portfolio.rules.Ast.Call;
import portfolio.rules.Ast.Expr;
import portfolio.rules.Ast.If;
import portfolio.rules.Ast.LiteralPattern;
import portfolio.rules.Ast.Match;
import portfolio.rules.Ast.Member;
import portfolio.rules.Ast.Name;
import portfolio.rules.Ast.NumberLit;
import portfolio.rules.Ast.Paren;
import portfolio.rules.Ast.TextLit;
import portfolio.rules.Ast.Wildcard;

class ParserTest {

    private static String canonical(String src) {
        return Printer.print(Parser.parse(src));
    }

    @ParameterizedTest(name = "{0}  =>  {1}")
    @CsvSource(delimiter = '|', textBlock = """
            1 + 2 * 3                      | (1 + (2 * 3))
            1 - 2 - 3                      | ((1 - 2) - 3)
            a - b + c                      | ((a - b) + c)
            8 / 4 / 2                      | ((8 / 4) / 2)
            a or b or c                    | ((a or b) or c)
            a % b / c                      | ((a % b) / c)
            (1 + 2) * 3                    | ((1 + 2) * 3)
            a or b and c                   | (a or (b and c))
            a and b or c                   | ((a and b) or c)
            not a == b                     | (not (a == b))
            not a and b                    | ((not a) and b)
            -a.b * 2                       | ((-a.b) * 2)
            - - 1                          | (-(-1))
            a.b.c                          | a.b.c
            1 + 2 < 3 * 4                  | ((1 + 2) < (3 * 4))
            x in [1, 2] or y               | ((x in [1, 2]) or y)
            min(a, b + 1)                  | min(a, (b + 1))
            if a then b else c + 1         | (if a then b else (c + 1))
            if a then b + 1 else c         | (if a then (b + 1) else c)
            if a then if b then c else d   | (if a then (if b then c else d))
            let x = 1; x + 1               | (let x = 1; (x + 1))
            x + if a then 1 else 2 * 3     | (x + (if a then 1 else (2 * 3)))
            [1, 2, ]                       | [1, 2]
            f()                            | f()
            """)
    void precedenceAndAssociativity(String src, String expected) {
        assertEquals(expected, canonical(src));
    }

    @Test
    void readmeExampleBuildsTheExpectedTree() {
        String src = "if customer.tier == \"gold\" then price * 0.9 else price";
        If rule = assertInstanceOf(If.class, Parser.parse(src));
        Binary cond = assertInstanceOf(Binary.class, rule.condition());
        assertEquals(BinaryOp.EQ, cond.op());
        Member tier = assertInstanceOf(Member.class, cond.left());
        assertEquals("tier", tier.field());
        assertEquals("customer", assertInstanceOf(Name.class, tier.target()).name());
        assertEquals("gold", assertInstanceOf(TextLit.class, cond.right()).value());
        Binary then = assertInstanceOf(Binary.class, rule.then());
        assertEquals(new BigDecimal("0.9"), assertInstanceOf(NumberLit.class, then.right()).value());
        assertEquals("price", assertInstanceOf(Name.class, rule.orElse()).name());
    }

    @Test
    void spansCoverTheSourceOfEachNode() {
        String src = "min((price + 1) * 2, customer.age)";
        Call call = assertInstanceOf(Call.class, Parser.parse(src));
        assertEquals(src, call.span().of(src));
        assertEquals("min", call.nameSpan().of(src));
        Binary times = assertInstanceOf(Binary.class, call.args().get(0));
        assertEquals("(price + 1) * 2", times.span().of(src));
        Paren group = assertInstanceOf(Paren.class, times.left());
        assertEquals("(price + 1)", group.span().of(src));
        assertEquals("price + 1", group.inner().span().of(src));
        Member age = assertInstanceOf(Member.class, call.args().get(1));
        assertEquals("customer.age", age.span().of(src));
        assertEquals("age", age.fieldSpan().of(src));
    }

    @Test
    void matchSupportsNegativeNumbersWildcardsAndTrailingCommas() {
        String src = "match quantity { -1 => 0, 0 => 1, _ => 2, }";
        Match m = assertInstanceOf(Match.class, Parser.parse(src));
        assertEquals(3, m.arms().size());
        LiteralPattern first = assertInstanceOf(LiteralPattern.class, m.arms().get(0).pattern());
        assertEquals(new BigDecimal("-1"), assertInstanceOf(NumberLit.class, first.literal()).value());
        assertEquals("-1", first.span().of(src));
        assertInstanceOf(Wildcard.class, m.arms().get(2).pattern());
        assertEquals("(match quantity { -1 => 0, 0 => 1, _ => 2 })", Printer.print(m));
    }

    @Test
    void ifWithoutElseParsesSoTheCheckerCanExplainIt() {
        If i = assertInstanceOf(If.class, Parser.parse("if a then b"));
        assertNull(i.orElse());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            1 +                  | expected an expression, found end of rule
            (1 + 2               | expected `)`, found end of rule
            1 < 2 < 3            | comparisons cannot be chained
            a == b != c          | comparisons cannot be chained
            price.)              | expected a field name after `.`, found `)`
            (a + b)(1)           | only builtin functions can be called
            a.b(1)               | only builtin functions can be called
            1 2                  | expected end of rule, found `2`
            1)                   | expected end of rule, found `)`
            _ + 1                | `_` can only be used as a match pattern
            match x { }          | match needs at least one arm
            match x { y => 1 }   | expected a pattern, found `y`
            match x { - y => 1 } | expected a number after `-` in a pattern
            match x { 1 2 }      | expected `=>`, found `2`
            let = 1; 2           | expected a name after `let`, found `=`
            let x = 1 x          | expected `;`, found `x`
            if a b               | expected `then`, found `b`
            [1, 2                | expected `]` or `,`, found end of rule
            min(1 2)             | expected `)` or `,`, found `2`
            then                 | expected an expression, found `then`
            """)
    void syntaxErrorsNameWhatWasExpected(String src, String message) {
        RuleSyntaxException e = assertThrows(RuleSyntaxException.class, () -> Parser.parse(src));
        assertEquals(message, e.getMessage());
    }

    @Test
    void chainedComparisonHintSuggestsAnd() {
        RuleSyntaxException e = assertThrows(RuleSyntaxException.class, () -> Parser.parse("18 <= age < 65"));
        assertEquals("<", e.diagnostic().span().of("18 <= age < 65"));
        assertTrue(e.diagnostic().hint().contains("and"));
    }

    @Test
    void unmatchedParenthesisHint() {
        RuleSyntaxException e = assertThrows(RuleSyntaxException.class, () -> Parser.parse("(1 + 2"));
        assertEquals("an earlier `(` is still open", e.diagnostic().hint());
    }

    @Test
    void nestingDeeperThanTheLimitIsRefused() {
        String ok = "(".repeat(Parser.MAX_DEPTH - 1) + "1" + ")".repeat(Parser.MAX_DEPTH - 1);
        Expr tree = Parser.parse(ok);
        assertEquals("1", Printer.print(tree));
        String deep = "(".repeat(Parser.MAX_DEPTH + 1) + "1" + ")".repeat(Parser.MAX_DEPTH + 1);
        assertTrue(assertThrows(RuleSyntaxException.class, () -> Parser.parse(deep)).getMessage()
                .startsWith("rule is nested too deeply"));
    }
}
