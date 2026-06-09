package com.example.sabona.mojBroj;

public class ExpressionEvaluator {

    public static class EvalException extends Exception {
        public EvalException(String msg) { super(msg); }
    }

    private final String expr;
    private int pos;

    public ExpressionEvaluator(String expression) {
        this.expr = expression.replaceAll("\\s+", "");
        this.pos  = 0;
    }

    public double evaluate() throws EvalException {
        if (expr.isEmpty()) throw new EvalException("Prazan izraz");
        double result = parseExpression();
        if (pos != expr.length()) {
            throw new EvalException("Neočekivani karakter na poziciji " + pos);
        }
        return result;
    }

    private double parseExpression() throws EvalException {
        double val = parseTerm();
        while (pos < expr.length()) {
            char c = expr.charAt(pos);
            if (c == '+') { pos++; val += parseTerm(); }
            else if (c == '-') { pos++; val -= parseTerm(); }
            else break;
        }
        return val;
    }

    private double parseTerm() throws EvalException {
        double val = parseFactor();
        while (pos < expr.length()) {
            char c = expr.charAt(pos);
            if (c == '*') { pos++; val *= parseFactor(); }
            else if (c == '/') {
                pos++;
                double divisor = parseFactor();
                if (divisor == 0) throw new EvalException("Deljenje nulom");
                val /= divisor;
            }
            else break;
        }
        return val;
    }

    private double parseFactor() throws EvalException {
        if (pos >= expr.length()) throw new EvalException("Neočekivani kraj izraza");

        char c = expr.charAt(pos);

        // Unary minus
        if (c == '-') {
            pos++;
            return -parseFactor();
        }

        // Zagrade
        if (c == '(') {
            pos++; // preskočimo '('
            double val = parseExpression();
            if (pos >= expr.length() || expr.charAt(pos) != ')')
                throw new EvalException("Nedostaje zatvorena zagrada");
            pos++; // preskočimo ')'
            return val;
        }

        // Broj
        if (Character.isDigit(c)) {
            int start = pos;
            while (pos < expr.length() && Character.isDigit(expr.charAt(pos))) pos++;
            return Double.parseDouble(expr.substring(start, pos));
        }

        throw new EvalException("Neočekivani karakter: '" + c + "'");
    }
}
