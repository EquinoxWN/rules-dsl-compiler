package portfolio.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DiagnosticRenderTest {

    @Test
    void underlinesTheOffendingCodeWithItsLabel() {
        Source src = Source.of("customer.tier * 2");
        Diagnostic d = Rules.check(src, TestSchemas.PRICING, null).diagnostics().getFirst();
        assertEquals("""
                error: cannot multiply text by number
                 --> rule:1:1
                  |
                1 | customer.tier * 2
                  | ^^^^^^^^^^^^^ this is text (tier: "gold" | "silver" | "bronze")
                """, d.render(src));
    }

    @Test
    void pointsAtTheRightLineAndColumnWithAHint() {
        Source src = new Source("discount.rule", "# discount\nlet rate = 0.1;\n\tprice * rat");
        Diagnostic d = Rules.check(src, TestSchemas.PRICING, null).diagnostics().getFirst();
        assertEquals("""
                error: unknown name `rat`
                 --> discount.rule:3:10
                  |
                3 |  price * rat
                  |          ^^^ not an input or a `let` name
                  = hint: did you mean `rate`?
                """, d.render(src));
    }

    @Test
    void columnsCountCharactersNotUtf16Units() {
        Source src = Source.of("\"😀\" + price");
        Diagnostic d = Rules.check(src, TestSchemas.PRICING, null).diagnostics().getFirst();
        String[] lines = d.render(src).split("\n");
        assertEquals(" --> rule:1:1", lines[1]);
        assertEquals("  | ^^^^^^^^^^^ text + number", lines[4]);
    }

    @Test
    void errorAtTheEndOfTheRuleShowsOneCaret() {
        Source src = Source.of("price *");
        Diagnostic d = Rules.check(src, TestSchemas.PRICING, null).diagnostics().getFirst();
        assertEquals("""
                error: expected an expression, found end of rule
                 --> rule:1:8
                  |
                1 | price *
                  |        ^ rule ends here
                """, d.render(src));
    }

    @Test
    void lineNumbersWiderThanOneDigitKeepTheGutterAligned() {
        Source src = Source.of("\n".repeat(11) + "pric");
        String[] lines = Rules.check(src, TestSchemas.PRICING, null).diagnostics().getFirst().render(src).split("\n");
        assertEquals("  --> rule:12:1", lines[1]);
        assertEquals("   |", lines[2]);
        assertEquals("12 | pric", lines[3]);
        assertEquals("   | ^^^^ not an input or a `let` name", lines[4]);
    }
}
