package portfolio.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import portfolio.rules.Ast.Expr;

/**
 * Properties over thousands of random trees: printing then parsing gives the same tree, and
 * every node's span, cut out of the source and parsed alone, gives that same node.
 */
class RoundTripPropertyTest {
    static final int CASES = 3000;

    @Test
    void printThenParseIsTheIdentity() {
        for (long seed = 0; seed < CASES; seed++) {
            Expr tree = new RandomRules(seed).expr(6);
            String printed = Printer.print(tree);
            String again = Printer.print(Parser.parse(printed));
            assertEquals(printed, again, "seed " + seed);
        }
    }

    @Test
    void everyNodeSpanReparsesToThatNode() {
        for (long seed = 0; seed < CASES; seed++) {
            String src = Printer.print(new RandomRules(seed).expr(5));
            assertSpansReparse(src, "seed " + seed);
        }
    }

    @Test
    void spansHoldForTheHandWrittenExamples() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("examples"))) {
            List<Path> rules = files.filter(p -> p.toString().endsWith(".rule")).toList();
            for (Path p : rules) {
                assertSpansReparse(Files.readString(p), p.toString());
            }
        }
    }

    private static void assertSpansReparse(String src, String context) {
        for (Expr node : Ast.all(Parser.parse(src))) {
            String cut = node.span().of(src);
            assertEquals(Printer.print(node), Printer.print(Parser.parse(cut)), context + ": " + cut);
        }
    }
}
