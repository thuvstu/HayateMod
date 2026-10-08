package com.thuvstu.hayatemod.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Safe arithmetic formulas for content amounts (mythic parity:
 * {@code lastDamage*0.5}, {@code 10+mana*0.2}). No code execution, no
 * field access: numbers, + - * / %, parentheses, unary minus, and
 * min/max/clamp calls over a fixed binding set.
 *
 * <p>Bindings: lastDamage, health (0-100), mana (0-100), stamina (0-100),
 * and {@code var_<name>} (owner var store, default 0).
 */
public final class Formula {
    private Formula() {
    }

    public static final Set<String> BINDINGS =
            Set.of("lastDamage", "health", "mana", "stamina");

    public static boolean isVar(String name) {
        return name.startsWith("var_") && name.length() > 4
                && name.substring(4).matches("[a-z_]+");
    }

    public static final class FormulaException extends Exception {
        FormulaException(String message) {
            super(message);
        }
    }

    /** Parses; throws with a human reason when the formula is unusable. */
    public static Node parse(String text) throws FormulaException {
        if (text == null || text.isBlank()) {
            throw new FormulaException("empty formula");
        }
        Parser p = new Parser(text);
        Node n = p.expr();
        p.ws();
        if (!p.end()) {
            throw new FormulaException("trailing text at '" + p.rest() + "'");
        }
        return n;
    }

    /** Parses, collecting referenced identifiers. */
    public static Set<String> identifiers(String text) throws FormulaException {
        var out = new java.util.HashSet<String>();
        parse(text).collect(out);
        return out;
    }

    public interface Node {
        double eval(Map<String, Double> bindings);

        void collect(java.util.Set<String> out);
    }

    private record Num(double value) implements Node {
        public double eval(Map<String, Double> bindings) {
            return value;
        }

        public void collect(java.util.Set<String> out) {
        }
    }

    private record Var(String name) implements Node {
        public double eval(Map<String, Double> bindings) {
            return bindings.getOrDefault(name, 0.0);
        }

        public void collect(java.util.Set<String> out) {
            out.add(name);
        }
    }

    private record Bin(char op, Node left, Node right) implements Node {
        public double eval(Map<String, Double> bindings) {
            double a = left.eval(bindings);
            double b = right.eval(bindings);
            return switch (op) {
                case '+' -> a + b;
                case '-' -> a - b;
                case '*' -> a * b;
                case '/' -> b == 0 ? 0 : a / b;
                case '%' -> b == 0 ? 0 : a % b;
                default -> 0;
            };
        }

        public void collect(java.util.Set<String> out) {
            left.collect(out);
            right.collect(out);
        }
    }

    private record Neg(Node inner) implements Node {
        public double eval(Map<String, Double> bindings) {
            return -inner.eval(bindings);
        }

        public void collect(java.util.Set<String> out) {
            inner.collect(out);
        }
    }

    private record Call(String name, List<Node> args) implements Node {
        public double eval(Map<String, Double> bindings) {
            List<Double> v = new ArrayList<>();
            for (Node n : args) {
                v.add(n.eval(bindings));
            }
            return switch (name) {
                case "min" -> v.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                case "max" -> v.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                case "clamp" -> v.size() == 3
                        ? Math.min(v.get(2), Math.max(v.get(0), v.get(1)))
                        : 0;
                default -> 0;
            };
        }

        public void collect(java.util.Set<String> out) {
            for (Node n : args) {
                n.collect(out);
            }
        }
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean end() {
            return i >= s.length();
        }

        String rest() {
            return s.substring(Math.min(i, s.length()));
        }

        void ws() {
            while (!end() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Node expr() throws FormulaException {
            Node n = term();
            for (;;) {
                ws();
                if (end() || (s.charAt(i) != '+' && s.charAt(i) != '-')) {
                    return n;
                }
                char op = s.charAt(i++);
                n = new Bin(op, n, term());
            }
        }

        Node term() throws FormulaException {
            Node n = factor();
            for (;;) {
                ws();
                if (end() || (s.charAt(i) != '*' && s.charAt(i) != '/' && s.charAt(i) != '%')) {
                    return n;
                }
                char op = s.charAt(i++);
                n = new Bin(op, n, factor());
            }
        }

        Node factor() throws FormulaException {
            ws();
            if (end()) {
                throw new FormulaException("unexpected end");
            }
            char c = s.charAt(i);
            if (c == '-') {
                i++;
                return new Neg(factor());
            }
            if (c == '(') {
                i++;
                Node n = expr();
                ws();
                if (end() || s.charAt(i) != ')') {
                    throw new FormulaException("missing ')'");
                }
                i++;
                return n;
            }
            if (Character.isDigit(c) || c == '.') {
                return number();
            }
            if (Character.isLetter(c) || c == '_') {
                return word();
            }
            throw new FormulaException("unexpected '" + c + "'");
        }

        Node number() throws FormulaException {
            int j = i;
            boolean dot = false;
            while (j < s.length()
                    && (Character.isDigit(s.charAt(j)) || (!dot && s.charAt(j) == '.'))) {
                if (s.charAt(j) == '.') {
                    dot = true;
                }
                j++;
            }
            try {
                double v = Double.parseDouble(s.substring(i, j));
                i = j;
                return new Num(v);
            } catch (NumberFormatException e) {
                throw new FormulaException("bad number");
            }
        }

        Node word() throws FormulaException {
            int j = i;
            while (j < s.length()
                    && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) {
                j++;
            }
            String w = s.substring(i, j);
            i = j;
            ws();
            if (!end() && s.charAt(i) == '(') {
                if (!w.equals("min") && !w.equals("max") && !w.equals("clamp")) {
                    throw new FormulaException("unknown function '" + w + "'");
                }
                i++;
                List<Node> args = new ArrayList<>();
                ws();
                if (!end() && s.charAt(i) != ')') {
                    args.add(expr());
                    for (;;) {
                        ws();
                        if (end()) {
                            throw new FormulaException("missing ')'");
                        }
                        if (s.charAt(i) == ')') {
                            break;
                        }
                        if (s.charAt(i) != ',') {
                            throw new FormulaException("expected ','");
                        }
                        i++;
                        args.add(expr());
                    }
                }
                if (end()) {
                    throw new FormulaException("missing ')'");
                }
                i++;
                if (w.equals("clamp") && args.size() != 3) {
                    throw new FormulaException("clamp needs 3 args");
                }
                if ((w.equals("min") || w.equals("max")) && args.isEmpty()) {
                    throw new FormulaException(w + " needs args");
                }
                return new Call(w, args);
            }
            if (!BINDINGS.contains(w) && !isVar(w)) {
                throw new FormulaException("unknown name '" + w + "'");
            }
            return new Var(w);
        }
    }
}
