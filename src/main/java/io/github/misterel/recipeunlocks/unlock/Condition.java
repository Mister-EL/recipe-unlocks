package io.github.misterel.recipeunlocks.unlock;

/** One step of an unlock rule: do {@code trigger} on {@code target} {@code count} times. */
public record Condition(Trigger trigger, Target target, int count) {
    /** Conditions with the same trigger and target share one counter, whatever the count. */
    public String counterKey() {
        return this.trigger.name() + " " + this.target;
    }
}
