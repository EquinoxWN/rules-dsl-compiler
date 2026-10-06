package portfolio.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import portfolio.rules.Ast.Binary;
import portfolio.rules.Ast.If;

class TypeCheckerTest {

    private static CheckResult check(String src) {
        return Rules.check(src, TestSchemas.PRICING, null);
    }

    private static List<String> errors(String src) {
        return check(src).diagnostics().stream().map(Diagnostic::message).toList();
    }

    private static Type typeOf(String src) {
        CheckResult r = check(src);
        assertTrue(r.ok(), () -> Rules.render(Source.of(src), r.diagnostics()));
        return r.type();
    }

    @Test
    void wellTypedRulesInferTheirResultType() {
        assertEquals(Type.NUMBER, typeOf("if customer.tier == \"gold\" then price * 0.9 else price"));
        assertEquals(Type.BOOL, typeOf("customer.age >= 18 and customer.country in [\"NL\", \"BE\"]"));
        assertEquals(Type.TEXT, typeOf("customer.country + \"-\" + customer.tier"));
        assertEquals(Type.NUMBER, typeOf("let price = \"x\"; len(price)"));
        assertEquals(Type.BOOL, typeOf("customer.tier == lower(customer.country)"));
        assertEquals(Type.BOOL, typeOf("quantity in []"));
        assertEquals(Type.NUMBER, typeOf("match customer.newsletter { true => 0.98, false => 1 }"));
        assertEquals(Type.NUMBER, typeOf("match quantity { 1 => 0, 2 => 1, _ => 2 }"));
        assertEquals(Type.NUMBER, typeOf("round(min(price, 10, quantity) / 3, 2)"));
        assertEquals(Type.BOOL, typeOf("startsWith(customer.tier, \"g\") or contains(customer.country, \"L\")"));
    }

    @Test
    void everyNodeGetsAType() {
        CheckResult r = check("if customer.tier == \"gold\" then price * 0.9 else price");
        If rule = assertInstanceOf(If.class, r.ast());
        Binary cond = assertInstanceOf(Binary.class, rule.condition());
        assertInstanceOf(Type.EnumT.class, r.typeOf(cond.left()));
        assertEquals(Type.BOOL, r.typeOf(cond));
        assertEquals(Type.NUMBER, r.typeOf(rule.then()));
        assertEquals(Ast.all(r.ast()).size(), r.types().size());
    }

    @Test
    void enumValuesCanBeUsedAsText() {
        assertTrue(Rules.check("customer.tier", TestSchemas.PRICING, Type.TEXT).ok());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            customer.tier * 2                               | cannot multiply text by number
            price / customer.country                        | cannot divide number by text
            price - customer.country                        | cannot subtract text from number
            price % true                                    | cannot take the remainder of number divided by bool
            price + customer.country                        | cannot add number and text
            customer.tier == "glod"                         | "glod" is not a valid tier
            "silvr" == customer.tier                        | "silvr" is not a valid tier
            customer.tier in ["gold", "platinum"]           | "platinum" is not a valid tier
            match customer.tier { "gold" => 1, "silver" => 2 } | match is not exhaustive: missing "bronze"
            match customer.newsletter { true => 1 }         | match is not exhaustive: missing false
            match quantity { 1 => 0, 2 => 1 }               | match on number needs a final `_ => ...` arm
            match quantity { _ => 0, 2 => 1 }               | unreachable arm: `_` above already matches everything
            match quantity { 1 => 0, 1.00 => 1, _ => 2 }    | duplicate arm: 1.00 is already handled
            match quantity { "a" => 1, _ => 2 }             | pattern is text but the match is on number
            match customer.tier { 1 => 1, _ => 2 }          | pattern is number but the match is on text
            match quantity { 1 => "one", _ => 2 }           | match arms have different types: text and number
            if customer.newsletter then price * 0.98        | `if` without `else` has no value when the condition is false
            if quantity > 10 then "bulk" else price         | the branches of `if` have different types: text and number
            if quantity then 1 else 2                       | the condition of `if` must be bool, found number
            pric * 2                                        | unknown name `pric`
            customer.tir                                    | unknown field `tir`
            price.amount                                    | number has no field `amount`
            quantity and true                               | `and` needs bool on both sides, found number
            not price                                       | `not` needs bool, found number
            -customer.country                               | `-` needs number, found text
            customer.country < "M"                          | `<` compares numbers, found text
            price == "10"                                   | cannot compare number with text
            customer == customer                            | cannot compare record with record
            price in 5                                      | `in` needs a list on the right, found number
            price in ["a"]                                  | cannot look for number in a list of text
            [1, "a"]                                        | list items must all have the same type: found number and text
            price / 0                                       | division by zero
            price % (0.0)                                   | division by zero
            mni(price, 1)                                   | unknown function `mni`
            round(price)                                    | `round` takes 2 arguments, found 1
            min(price)                                      | `min` takes at least 2 arguments, found 1
            abs("x")                                        | argument 1 of `abs` must be number, found text
            len(price)                                      | argument 1 of `len` must be text or list, found number
            round(price, 2.5)                               | `round` digits must be a whole number from 0 to 10
            (let a = 1; a) + a                              | unknown name `a`
            """)
    void mistakesAreReportedWithAPreciseMessage(String src, String message) {
        assertEquals(List.of(message), errors(src));
    }

    @Test
    void suggestionsPointAtTheClosestName() {
        assertEquals("did you mean `price`?", check("pric * 2").diagnostics().getFirst().hint());
        assertEquals("did you mean `tier`?", check("customer.tir").diagnostics().getFirst().hint());
        assertEquals("did you mean \"gold\"?", check("customer.tier == \"glod\"").diagnostics().getFirst().hint());
        assertEquals("did you mean `min`?", check("mni(price, 1)").diagnostics().getFirst().hint());
        assertEquals("the inputs are price, quantity, customer",
                check("zzzzzz").diagnostics().getFirst().hint());
    }

    @Test
    void theOffendingOperandIsUnderlined() {
        String src = "customer.tier * 2";
        Diagnostic d = check(src).diagnostics().getFirst();
        assertEquals("customer.tier", d.span().of(src));
        assertEquals("this is text (tier: \"gold\" | \"silver\" | \"bronze\")", d.label());
    }

    @Test
    void allIndependentMistakesAreReportedInSourceOrder() {
        assertEquals(List.of(
                "the condition of `if` must be bool, found number",
                "unknown name `pric`",
                "argument 1 of `upper` must be text, found number"),
                errors("if customer.age then pric * 2 else upper(price)"));
    }

    @Test
    void oneMistakeDoesNotCascade() {
        assertEquals(List.of("unknown name `pric`"), errors("(pric * 2 + 1) / 3 > 3 and not (len(\"a\") < 0)"));
    }

    @Test
    void theHostCanRequireAResultType() {
        CheckResult r = Rules.check("customer.age >= 18", TestSchemas.PRICING, Type.NUMBER);
        assertEquals(List.of("this rule must produce number, found bool"),
                r.diagnostics().stream().map(Diagnostic::message).toList());
    }
}
