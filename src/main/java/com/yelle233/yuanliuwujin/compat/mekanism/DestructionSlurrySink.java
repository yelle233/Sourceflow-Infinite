package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.SlurryStack;

import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 销毁机器的 Mekanism Slurry 虚空 Handler（1.20.1 Forge 版本）。
 */
public class DestructionSlurrySink implements ISlurryHandler {

    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<Long> budgetSupplier;
    private final LongConsumer budgetConsumer;

    public DestructionSlurrySink(Supplier<Boolean> canWorkSupplier,
                                  Supplier<Long> budgetSupplier,
                                  LongConsumer budgetConsumer) {
        this.canWorkSupplier = canWorkSupplier;
        this.budgetSupplier  = budgetSupplier;
        this.budgetConsumer  = budgetConsumer;
    }

    @Override
    public int getTanks() {
        return canWorkSupplier.get() ? 1 : 0;
    }

    @Override
    public SlurryStack getChemicalInTank(int tank) {
        return SlurryStack.EMPTY;
    }

    @Override
    public void setChemicalInTank(int tank, SlurryStack stack) {}

    @Override
    public long getTankCapacity(int tank) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isValid(int tank, SlurryStack stack) {
        return canWorkSupplier.get();
    }

    @Override
    public SlurryStack insertChemical(int tank, SlurryStack stack, Action action) {
        if (!canWorkSupplier.get() || stack.isEmpty()) return stack;
        long budget = budgetSupplier.get();
        long amt = Math.min(stack.getAmount(), budget);
        if (amt <= 0) return stack;
        if (action.execute()) budgetConsumer.accept(amt);
        long remaining = stack.getAmount() - amt;
        if (remaining <= 0) return SlurryStack.EMPTY;
        return new SlurryStack(stack.getType(), remaining);
    }

    @Override
    public SlurryStack extractChemical(int tank, long amount, Action action) {
        return SlurryStack.EMPTY;
    }

    @Override
    public SlurryStack insertChemical(SlurryStack stack, Action action) {
        return insertChemical(0, stack, action);
    }

    @Override
    public SlurryStack extractChemical(long amount, Action action) {
        return SlurryStack.EMPTY;
    }
}
