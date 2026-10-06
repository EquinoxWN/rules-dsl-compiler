package portfolio.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Types of rule values. */
public sealed interface Type permits Type.Primitive, Type.EnumT, Type.ListT, Type.RecordT, Type.Special {

    /** Short name used in messages, such as "number". */
    String display();

    /** Longer description used in labels, such as the values of an enum. */
    default String detail() {
        return display();
    }

    /** Numbers, text and booleans. */
    enum Primitive implements Type {
        NUMBER, TEXT, BOOL;

        @Override
        public String display() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Text restricted to a fixed set of values, such as a customer tier. */
    record EnumT(String name, List<String> variants) implements Type {
        public EnumT {
            variants = List.copyOf(variants);
        }

        @Override
        public String display() {
            return "text";
        }

        @Override
        public String detail() {
            return "text (" + name + ": " + variants.stream().map(Printer::quote).collect(Collectors.joining(" | "))
                    + ")";
        }
    }

    /** List whose items all share one type. */
    record ListT(Type element) implements Type {
        @Override
        public String display() {
            return element == Special.NEVER ? "empty list" : "list of " + element.display();
        }
    }

    /** Named fields, such as the customer input. */
    record RecordT(Map<String, Type> fields) implements Type {
        public RecordT {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        @Override
        public String display() {
            return "record";
        }

        @Override
        public String detail() {
            return "record { " + String.join(", ", fields.keySet()) + " }";
        }
    }

    /** ERROR marks code that already has a reported error; NEVER is the item type of an empty list. */
    enum Special implements Type {
        ERROR, NEVER;

        @Override
        public String display() {
            return this == ERROR ? "unknown" : "nothing";
        }
    }

    Type NUMBER = Primitive.NUMBER;
    Type TEXT = Primitive.TEXT;
    Type BOOL = Primitive.BOOL;
    Type ERROR = Special.ERROR;
    Type NEVER = Special.NEVER;

    /** Text or an enum, which is a kind of text. */
    static boolean isTextLike(Type t) {
        return t == TEXT || t instanceof EnumT;
    }

    /** Smallest type both values fit, or null when they have nothing in common. */
    static Type join(Type a, Type b) {
        if (a == ERROR || b == ERROR) {
            return ERROR;
        }
        if (a == NEVER) {
            return b;
        }
        if (b == NEVER || a.equals(b)) {
            return a;
        }
        if (isTextLike(a) && isTextLike(b)) {
            return TEXT;
        }
        if (a instanceof ListT la && b instanceof ListT lb) {
            Type e = join(la.element(), lb.element());
            return e == null ? null : new ListT(e);
        }
        return null;
    }

    /** Whether a value of type actual can be used where expected is required. */
    static boolean assignable(Type actual, Type expected) {
        if (actual == ERROR || actual.equals(expected)) {
            return true;
        }
        Type j = join(actual, expected);
        return j != null && j.equals(expected);
    }
}
