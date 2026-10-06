package portfolio.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * Rules come from people who are not engineers, so no input may crash the checker: random
 * text, random token soup and pathological nesting must all end in diagnostics, never in an
 * exception or a stack overflow.
 */
class RobustnessTest {
    private static final String[] SOUP = {"if ", "then ", "else ", "let ", "match ", "and ", "or ", "not ", "in ",
        "true ", "false ", "_ ", "(", ")", "[", "]", "{", "}", ",", ".", ":", ";", "=>", "=", "+", "-", "*", "/",
        "%", "==", "!=", "<", "<=", ">", ">=", "price ", "customer", ".tier ", "\"gold\" ", "0.9 ", "12 ", "min(",
        "\"", "\\", "#", "\n", " ", "😀", "é", "@", "!"};

    @Test
    void randomTextNeverCrashesTheChecker() {
        Random random = new Random(42);
        for (int i = 0; i < 5000; i++) {
            StringBuilder sb = new StringBuilder();
            for (int n = random.nextInt(40); n > 0; n--) {
                sb.appendCodePoint(random.nextInt(4) == 0 ? random.nextInt(0x3000) : 32 + random.nextInt(95));
            }
            assertCheckedSafely(sb.toString());
        }
    }

    @Test
    void randomTokenSoupNeverCrashesTheChecker() {
        Random random = new Random(7);
        for (int i = 0; i < 20000; i++) {
            StringBuilder sb = new StringBuilder();
            for (int n = random.nextInt(30); n > 0; n--) {
                sb.append(SOUP[random.nextInt(SOUP.length)]);
            }
            assertCheckedSafely(sb.toString());
        }
    }

    @Test
    void randomTreesNeverCrashTheTypeChecker() {
        for (long seed = 0; seed < 3000; seed++) {
            assertCheckedSafely(Printer.print(new RandomRules(seed).expr(6)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"paren", "neg", "not", "member", "plus", "list", "if", "let", "call"})
    void pathologicalNestingEndsInADiagnostic(String shape) {
        int n = 20_000;
        String src = switch (shape) {
            case "paren" -> "(".repeat(n) + "1" + ")".repeat(n);
            case "neg" -> "-".repeat(n) + "1";
            case "not" -> "not ".repeat(n / 2) + "true";
            case "member" -> "customer" + ".tier".repeat(n / 3);
            case "plus" -> "1" + "+1".repeat(n);
            case "list" -> "[".repeat(n) + "]".repeat(n);
            case "if" -> "if true then ".repeat(n / 15) + "1";
            case "let" -> "let a = 1; ".repeat(n / 12) + "a";
            default -> "abs(".repeat(n / 5) + "1" + ")".repeat(n / 5);
        };
        CheckResult r = assertDoesNotThrow(() -> Rules.check(src, TestSchemas.PRICING, null));
        assertEquals(1, r.diagnostics().size(), shape);
        assertTrue(r.diagnostics().getFirst().message().startsWith("rule is nested too deeply"),
                r.diagnostics().getFirst().message());
    }

    @Test
    void longButFlatRulesAreFine() {
        String src = "price in [" + "1, ".repeat(10_000) + "2]";
        assertTrue(Rules.check(src, TestSchemas.PRICING, Type.BOOL).ok());
    }

    private static void assertCheckedSafely(String src) {
        CheckResult r = assertDoesNotThrow(() -> Rules.check(src, TestSchemas.PRICING, null), src);
        Source source = Source.of(src);
        for (Diagnostic d : r.diagnostics()) {
            assertTrue(d.span().start() >= 0 && d.span().end() <= src.length(), src);
            assertDoesNotThrow(() -> d.render(source), src);
        }
        assertTrue(r.ok() == (r.ast() != null && r.diagnostics().isEmpty()));
    }
}
