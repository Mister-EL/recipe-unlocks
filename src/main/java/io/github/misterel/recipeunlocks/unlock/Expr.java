package io.github.misterel.recipeunlocks.unlock;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** An unlock rule: conditions joined with AND / OR. */
public sealed interface Expr {
    boolean test(ToIntFunction<Condition> progress);

    void forEachCondition(Consumer<Condition> out);

    /** Text form of the rule; with {@code progress} every condition shows "done/needed". */
    String describe(@Nullable ToIntFunction<Condition> progress);

    record Leaf(Condition condition) implements Expr {
        @Override
        public boolean test(ToIntFunction<Condition> progress) {
            return progress.applyAsInt(this.condition) >= this.condition.count();
        }

        @Override
        public void forEachCondition(Consumer<Condition> out) {
            out.accept(this.condition);
        }

        @Override
        public String describe(@Nullable ToIntFunction<Condition> progress) {
            int need = this.condition.count();
            String amount = progress == null ? String.valueOf(need) : Math.min(progress.applyAsInt(this.condition), need) + "/" + need;
            return this.condition.counterKey() + " " + amount;
        }
    }

    record And(List<Expr> parts) implements Expr {
        @Override
        public boolean test(ToIntFunction<Condition> progress) {
            return this.parts.stream().allMatch(part -> part.test(progress));
        }

        @Override
        public void forEachCondition(Consumer<Condition> out) {
            this.parts.forEach(part -> part.forEachCondition(out));
        }

        @Override
        public String describe(@Nullable ToIntFunction<Condition> progress) {
            return this.parts.stream()
                    .map(part -> part instanceof Or ? "(" + part.describe(progress) + ")" : part.describe(progress))
                    .collect(Collectors.joining(" & "));
        }
    }

    record Or(List<Expr> parts) implements Expr {
        @Override
        public boolean test(ToIntFunction<Condition> progress) {
            return this.parts.stream().anyMatch(part -> part.test(progress));
        }

        @Override
        public void forEachCondition(Consumer<Condition> out) {
            this.parts.forEach(part -> part.forEachCondition(out));
        }

        @Override
        public String describe(@Nullable ToIntFunction<Condition> progress) {
            return this.parts.stream().map(part -> part.describe(progress)).collect(Collectors.joining(" | "));
        }
    }
}
