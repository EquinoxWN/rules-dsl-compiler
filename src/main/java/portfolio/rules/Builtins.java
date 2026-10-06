package portfolio.rules;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The only functions a rule may call, with their signatures. */
final class Builtins {
    private Builtins() {
    }

    /** What an argument position accepts. */
    enum Param {
        NUMBER("number"), TEXT("text"), TEXT_OR_LIST("text or list");

        final String label;

        Param(String label) {
            this.label = label;
        }

        boolean accepts(Type t) {
            return switch (this) {
                case NUMBER -> t == Type.NUMBER;
                case TEXT -> Type.isTextLike(t);
                case TEXT_OR_LIST -> Type.isTextLike(t) || t instanceof Type.ListT;
            };
        }
    }

    /** One builtin: parameters (the last repeats when variadic), result type and usage line. */
    record Signature(String usage, List<Param> params, boolean variadic, Type result) {
        int minArgs() {
            return params.size();
        }

        int maxArgs() {
            return variadic ? Integer.MAX_VALUE : params.size();
        }

        Param param(int i) {
            return params.get(Math.min(i, params.size() - 1));
        }
    }

    private static Signature sig(String usage, Type result, Param... params) {
        return new Signature(usage, List.of(params), false, result);
    }

    static final Map<String, Signature> ALL = new TreeMap<>(Map.ofEntries(
            Map.entry("abs", sig("abs(number)", Type.NUMBER, Param.NUMBER)),
            Map.entry("ceil", sig("ceil(number)", Type.NUMBER, Param.NUMBER)),
            Map.entry("floor", sig("floor(number)", Type.NUMBER, Param.NUMBER)),
            Map.entry("round", sig("round(number, digits)", Type.NUMBER, Param.NUMBER, Param.NUMBER)),
            Map.entry("min", new Signature("min(number, number, ...)", List.of(Param.NUMBER, Param.NUMBER), true,
                    Type.NUMBER)),
            Map.entry("max", new Signature("max(number, number, ...)", List.of(Param.NUMBER, Param.NUMBER), true,
                    Type.NUMBER)),
            Map.entry("len", sig("len(text or list)", Type.NUMBER, Param.TEXT_OR_LIST)),
            Map.entry("lower", sig("lower(text)", Type.TEXT, Param.TEXT)),
            Map.entry("upper", sig("upper(text)", Type.TEXT, Param.TEXT)),
            Map.entry("trim", sig("trim(text)", Type.TEXT, Param.TEXT)),
            Map.entry("startsWith", sig("startsWith(text, prefix)", Type.BOOL, Param.TEXT, Param.TEXT)),
            Map.entry("endsWith", sig("endsWith(text, suffix)", Type.BOOL, Param.TEXT, Param.TEXT)),
            Map.entry("contains", sig("contains(text, part)", Type.BOOL, Param.TEXT, Param.TEXT))));
}
