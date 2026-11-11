package io.github.misterel.recipeunlocks.unlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses unlock rules such as {@code break stone 3 & (eat apple | drop torch)}.
 *
 * <pre>
 * rule      := and ( OR and )*
 * and       := factor ( AND factor )*
 * factor    := "(" rule ")" | condition
 * condition := template target [count]
 * </pre>
 */
public final class ExprParser {
    private static final Set<String> AND_WORDS = Set.of("&", "+", ",", "and", "и");
    private static final Set<String> OR_WORDS = Set.of("|", "or", "или");
    private static final String SYMBOLS = "()&|+,";

    public static final class SyntaxException extends Exception {
        public SyntaxException(String message) {
            super(message);
        }
    }

    private record Token(String text, int position) {}

    private final List<Token> tokens;
    private int index;

    private ExprParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Expr parse(String text) throws SyntaxException {
        ExprParser parser = new ExprParser(tokenize(text));
        if (parser.tokens.isEmpty()) {
            throw new SyntaxException("пустое условие");
        }
        Expr expr = parser.parseOr();
        Token rest = parser.peek();
        if (rest != null) {
            throw parser.error(rest, isOperand(rest) ? "между условиями нужен & или |, а встретилось '" + rest.text + "'" : "лишнее '" + rest.text + "'");
        }
        return expr;
    }

    private static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (SYMBOLS.indexOf(c) >= 0) {
                // "&&" and "||" mean the same as "&" and "|".
                boolean doubled = (c == '&' || c == '|') && !tokens.isEmpty() && i > 0 && text.charAt(i - 1) == c
                        && tokens.getLast().position == i - 1;
                if (!doubled) {
                    tokens.add(new Token(String.valueOf(c), i));
                }
                i++;
            } else {
                int start = i;
                while (i < text.length() && !Character.isWhitespace(text.charAt(i)) && SYMBOLS.indexOf(text.charAt(i)) < 0) {
                    i++;
                }
                tokens.add(new Token(text.substring(start, i), start));
            }
        }
        return tokens;
    }

    private Expr parseOr() throws SyntaxException {
        List<Expr> parts = new ArrayList<>();
        parts.add(this.parseAnd());
        while (this.peekIs(OR_WORDS)) {
            this.index++;
            parts.add(this.parseAnd());
        }
        return parts.size() == 1 ? parts.getFirst() : new Expr.Or(List.copyOf(parts));
    }

    private Expr parseAnd() throws SyntaxException {
        List<Expr> parts = new ArrayList<>();
        parts.add(this.parseFactor());
        while (this.peekIs(AND_WORDS)) {
            this.index++;
            parts.add(this.parseFactor());
        }
        return parts.size() == 1 ? parts.getFirst() : new Expr.And(List.copyOf(parts));
    }

    private Expr parseFactor() throws SyntaxException {
        Token token = this.next("ожидалось условие");
        if (token.text.equals("(")) {
            Expr inner = this.parseOr();
            Token close = this.next("не закрыта скобка '('");
            if (!close.text.equals(")")) {
                throw this.error(close, "ожидалась ')', а встретилось '" + close.text + "'");
            }
            return inner;
        }
        if (!isOperand(token)) {
            throw this.error(token, "ожидалось условие, а встретилось '" + token.text + "'");
        }
        Trigger trigger = Trigger.resolve(token.text);
        if (trigger == null) {
            throw this.error(token, "неизвестный шаблон '" + token.text + "'. Доступны: " + Trigger.knownNames());
        }
        Token targetToken = this.peek();
        if (targetToken == null || !isOperand(targetToken)) {
            throw this.error(token, "после '" + token.text + "' нужна цель: id, #тег или *");
        }
        this.index++;
        Target target = Target.parse(targetToken.text);
        if (target == null) {
            throw this.error(targetToken, "'" + targetToken.text + "' не похоже на id, #тег или *");
        }
        int count = 1;
        Token countToken = this.peek();
        if (countToken != null && countToken.text.chars().allMatch(Character::isDigit)) {
            this.index++;
            try {
                count = Integer.parseInt(countToken.text);
            } catch (NumberFormatException e) {
                count = 0;
            }
            if (count < 1) {
                throw this.error(countToken, "количество должно быть от 1 до " + Integer.MAX_VALUE);
            }
        }
        return new Expr.Leaf(new Condition(trigger, target, count));
    }

    private static boolean isOperand(Token token) {
        String text = token.text.toLowerCase(Locale.ROOT);
        return !text.equals("(") && !text.equals(")") && !AND_WORDS.contains(text) && !OR_WORDS.contains(text);
    }

    private Token peek() {
        return this.index < this.tokens.size() ? this.tokens.get(this.index) : null;
    }

    private boolean peekIs(Set<String> words) {
        Token token = this.peek();
        return token != null && words.contains(token.text.toLowerCase(Locale.ROOT));
    }

    private Token next(String messageIfMissing) throws SyntaxException {
        Token token = this.peek();
        if (token == null) {
            throw new SyntaxException("в конце строки: " + messageIfMissing);
        }
        this.index++;
        return token;
    }

    private SyntaxException error(Token token, String message) {
        return new SyntaxException("символ " + (token.position + 1) + ": " + message);
    }
}
