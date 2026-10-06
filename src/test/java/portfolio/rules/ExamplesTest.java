package portfolio.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * The example rules in examples/ are documentation, so they are tested: valid ones must check,
 * and every broken one must print exactly the diagnostics stored next to it (snapshot tests).
 * Run with -Dsnapshots.update=true to rewrite the .expected files after an intended change.
 */
class ExamplesTest {
    private static final Path DIR = Path.of("examples");
    private static final Map<String, Type> VALID = Map.of(
            "gold-discount.rule", Type.NUMBER,
            "tier-discount.rule", Type.NUMBER,
            "eligible.rule", Type.BOOL);

    private static Schema schema() throws IOException {
        return Schema.parse(Files.readString(DIR.resolve("pricing.schema")));
    }

    @TestFactory
    Stream<DynamicTest> validExamplesCheckAgainstTheSchema() throws IOException {
        Schema schema = schema();
        return VALID.entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), () -> {
            Source src = new Source(e.getKey(), Files.readString(DIR.resolve(e.getKey())));
            CheckResult r = Rules.check(src, schema, e.getValue());
            assertTrue(r.ok(), () -> Rules.render(src, r.diagnostics()));
        }));
    }

    @TestFactory
    Stream<DynamicTest> brokenExamplesPrintTheirSnapshot() throws IOException {
        Schema schema = schema();
        boolean update = Boolean.getBoolean("snapshots.update");
        List<Path> rules;
        try (Stream<Path> files = Files.list(DIR.resolve("errors"))) {
            rules = files.filter(p -> p.toString().endsWith(".rule")).sorted().toList();
        }
        assertFalse(rules.isEmpty());
        return rules.stream().map(rule -> DynamicTest.dynamicTest(rule.getFileName().toString(), () -> {
            String name = "examples/errors/" + rule.getFileName();
            Source src = new Source(name, Files.readString(rule, StandardCharsets.UTF_8));
            Type expect = rule.getFileName().toString().equals("wrong-result-type.rule") ? Type.NUMBER : null;
            CheckResult r = Rules.check(src, schema, expect);
            assertFalse(r.ok(), name + " should not check");
            String actual = Rules.render(src, r.diagnostics());
            Path expected = Path.of(rule.toString().replace(".rule", ".expected"));
            if (update) {
                Files.writeString(expected, actual, StandardCharsets.UTF_8);
            }
            assertEquals(Files.readString(expected, StandardCharsets.UTF_8).replace("\r\n", "\n"), actual);
        }));
    }
}
