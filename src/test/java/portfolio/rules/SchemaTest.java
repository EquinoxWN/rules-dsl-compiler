package portfolio.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SchemaTest {

    @Test
    void parsesNestedRecordsEnumsAndLists() {
        Schema s = Schema.parse("order: { lines: list(number), status: enum(\"open\", \"paid\") }, vip: bool");
        Type.RecordT order = (Type.RecordT) s.root().fields().get("order");
        assertEquals(new Type.ListT(Type.NUMBER), order.fields().get("lines"));
        assertEquals(new Type.EnumT("status", List.of("open", "paid")), order.fields().get("status"));
        assertEquals(Type.BOOL, s.root().fields().get("vip"));
        assertEquals(List.of("order", "vip"), List.copyOf(s.root().fields().keySet()));
    }

    @Test
    void rulesCanReadNestedListsFromTheSchema() {
        Schema s = Schema.parse("order: { lines: list(number) }");
        assertTrue(Rules.check("len(order.lines) > 0 and 5 in order.lines", s, Type.BOOL).ok());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            a: number, a: text          | duplicate field `a`
            a: date                     | unknown type `date`
            a: enum()                   | enum values must be text like "gold", found `)`
            a: enum("x", "x")           | duplicate enum value "x"
            a number                    | expected `:`, found `number`
            a: { b: number              | expected a field name, found end of rule
            """)
    void invalidSchemasAreRejectedWithTheReason(String text, String message) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Schema.parse(text));
        assertTrue(e.getMessage().contains("error: " + message), e.getMessage());
        assertTrue(e.getMessage().contains("--> schema:1:"), e.getMessage());
    }
}
