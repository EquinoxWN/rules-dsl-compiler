package portfolio.rules;

/** The pricing schema used across the tests (same as examples/pricing.schema). */
final class TestSchemas {
    private TestSchemas() {
    }

    static final Schema PRICING = Schema.parse("""
            price: number
            quantity: number
            customer: {
              tier: enum("gold", "silver", "bronze"),
              age: number,
              country: text,
              newsletter: bool
            }
            """);
}
