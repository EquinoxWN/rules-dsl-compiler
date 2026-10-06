package portfolio.rules;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Command line: check a rule against a schema, or print how it was parsed. */
public final class Main {
    private static final long MAX_FILE_BYTES = 1L << 20;
    private static final String USAGE = """
            usage: rules check --schema <schema-file> [--expect number|text|bool] <rule-file>
                   rules ast <rule-file>
            """;

    private Main() {
    }

    /** Process entry point. */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Run a command; returns 0 when the rule is valid, 1 for rule errors, 2 for usage errors. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            err.print(USAGE);
            return 2;
        }
        try {
            return switch (args[0]) {
                case "check" -> check(args, out, err);
                case "ast" -> ast(args, out, err);
                default -> {
                    err.print(USAGE);
                    yield 2;
                }
            };
        } catch (IOException | IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            return 2;
        }
    }

    private static int check(String[] args, PrintStream out, PrintStream err) throws IOException {
        String schemaFile = null;
        String expect = null;
        String ruleFile = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--schema" -> schemaFile = value(args, ++i);
                case "--expect" -> expect = value(args, ++i);
                default -> ruleFile = args[i];
            }
        }
        if (schemaFile == null || ruleFile == null) {
            err.print(USAGE);
            return 2;
        }
        Schema schema = Schema.parse(read(schemaFile));
        Type expected = expect == null ? null : switch (expect) {
            case "number" -> Type.NUMBER;
            case "text" -> Type.TEXT;
            case "bool" -> Type.BOOL;
            default -> throw new IllegalArgumentException("--expect must be number, text or bool");
        };
        Source source = new Source(ruleFile.replace('\\', '/'), read(ruleFile));
        CheckResult result = Rules.check(source, schema, expected);
        if (result.ok()) {
            out.println("ok: " + result.type().detail());
            return 0;
        }
        int n = result.diagnostics().size();
        err.print(Rules.render(source, result.diagnostics()));
        err.println(n + " error" + (n == 1 ? "" : "s"));
        return 1;
    }

    private static int ast(String[] args, PrintStream out, PrintStream err) throws IOException {
        if (args.length != 2) {
            err.print(USAGE);
            return 2;
        }
        Source source = new Source(args[1].replace('\\', '/'), read(args[1]));
        try {
            out.println(Printer.print(Rules.parse(source.text())));
            return 0;
        } catch (RuleSyntaxException e) {
            err.print(e.diagnostic().render(source));
            return 1;
        }
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            throw new IllegalArgumentException("missing value after " + args[i - 1]);
        }
        return args[i];
    }

    private static String read(String file) throws IOException {
        Path p = Path.of(file);
        if (Files.size(p) > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(file + " is larger than 1 MiB");
        }
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
