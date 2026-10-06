package portfolio.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MainTest {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void validRulePrintsItsType() {
        assertEquals(0, run("check", "--schema", "examples/pricing.schema", "--expect", "number",
                "examples/tier-discount.rule"));
        assertEquals("ok: number\n", out());
    }

    @Test
    void brokenRulePrintsDiagnosticsAndExitsWithOne() {
        assertEquals(1, run("check", "--schema", "examples/pricing.schema", "examples/errors/several-errors.rule"));
        assertTrue(err().contains("--> examples/errors/several-errors.rule:1:4"), err());
        assertTrue(err().endsWith("3 errors\n"), err());
    }

    @Test
    void astShowsHowTheRuleWasGrouped() {
        assertEquals(0, run("ast", "examples/gold-discount.rule"));
        assertEquals("(if (customer.tier == \"gold\") then (price * 0.9) else price)\n", out());
    }

    @Test
    void usageErrorsExitWithTwo() {
        assertEquals(2, run());
        assertEquals(2, run("check", "examples/gold-discount.rule"));
        assertEquals(2, run("check", "--schema", "examples/pricing.schema", "--expect", "date",
                "examples/gold-discount.rule"));
        assertEquals(2, run("check", "--schema", "missing.schema", "examples/gold-discount.rule"));
        assertEquals(2, run("explode"));
        assertTrue(err().contains("usage: rules check"), err());
    }
}
