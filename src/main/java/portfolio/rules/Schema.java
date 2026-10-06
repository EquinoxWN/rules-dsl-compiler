package portfolio.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import portfolio.rules.Token.Kind;

/** The inputs a rule may read, with their types; supplied by the host application. */
public record Schema(Type.RecordT root) {

    private static final int MAX_NESTING = 32;

    /** Schema from a map of top-level inputs. */
    public static Schema of(Map<String, Type> inputs) {
        return new Schema(new Type.RecordT(inputs));
    }

    /**
     * Parse a schema such as {@code price: number, customer: { tier: enum("gold", "silver") }};
     * throws IllegalArgumentException with a rendered diagnostic when it is invalid.
     */
    public static Schema parse(String text) {
        try {
            SchemaParser p = new SchemaParser(Lexer.lex(text));
            return of(p.fields(Kind.EOF, 0));
        } catch (RuleSyntaxException e) {
            throw new IllegalArgumentException("invalid schema\n" + e.diagnostic().render(new Source("schema", text)),
                    e);
        }
    }

    /** Recursive-descent parser for the small schema language. */
    private static final class SchemaParser {
        private final List<Token> tokens;
        private int pos;

        SchemaParser(List<Token> tokens) {
            this.tokens = tokens;
        }

        Map<String, Type> fields(Kind end, int depth) {
            if (depth > MAX_NESTING) {
                throw error("schema is nested too deeply", peek().span(), "too deep");
            }
            Map<String, Type> fields = new LinkedHashMap<>();
            while (peek().kind() != end) {
                Token name = next();
                if (name.kind() != Kind.IDENT) {
                    throw error("expected a field name, found " + name.describe(), name.span(), "expected a name");
                }
                expect(Kind.COLON, "`:`");
                Type type = type(name, depth);
                if (fields.put((String) name.value(), type) != null) {
                    throw error("duplicate field `" + name.value() + "`", name.span(), "already declared");
                }
                if (peek().kind() == Kind.COMMA) {
                    next();
                }
            }
            return fields;
        }

        Type type(Token field, int depth) {
            Token t = next();
            if (t.kind() == Kind.LBRACE) {
                Map<String, Type> nested = fields(Kind.RBRACE, depth + 1);
                expect(Kind.RBRACE, "`}`");
                return new Type.RecordT(nested);
            }
            if (t.kind() != Kind.IDENT) {
                throw error("expected a type, found " + t.describe(), t.span(),
                        "use number, text, bool, enum(...), list(...) or { ... }");
            }
            return switch ((String) t.value()) {
                case "number" -> Type.NUMBER;
                case "text" -> Type.TEXT;
                case "bool" -> Type.BOOL;
                case "list" -> {
                    expect(Kind.LPAREN, "`(`");
                    Type element = type(field, depth + 1);
                    expect(Kind.RPAREN, "`)`");
                    yield new Type.ListT(element);
                }
                case "enum" -> enumType(field);
                default -> throw error("unknown type `" + t.value() + "`", t.span(),
                        "use number, text, bool, enum(...), list(...) or { ... }");
            };
        }

        private Type enumType(Token field) {
            expect(Kind.LPAREN, "`(`");
            Set<String> variants = new LinkedHashSet<>();
            while (true) {
                Token v = next();
                if (v.kind() != Kind.TEXT) {
                    throw error("enum values must be text like \"gold\", found " + v.describe(), v.span(),
                            "expected text");
                }
                if (!variants.add((String) v.value())) {
                    throw error("duplicate enum value " + Printer.quote((String) v.value()), v.span(),
                            "already listed");
                }
                if (peek().kind() != Kind.COMMA) {
                    break;
                }
                next();
            }
            expect(Kind.RPAREN, "`)`");
            return new Type.EnumT((String) field.value(), new ArrayList<>(variants));
        }

        private Token peek() {
            return tokens.get(pos);
        }

        private Token next() {
            Token t = tokens.get(pos);
            if (t.kind() != Kind.EOF) {
                pos++;
            }
            return t;
        }

        private Token expect(Kind k, String what) {
            Token t = next();
            if (t.kind() != k) {
                throw error("expected " + what + ", found " + t.describe(), t.span(), "expected " + what);
            }
            return t;
        }

        private static RuleSyntaxException error(String message, Span span, String label) {
            return new RuleSyntaxException(Diagnostic.error(message, span, label));
        }
    }
}
