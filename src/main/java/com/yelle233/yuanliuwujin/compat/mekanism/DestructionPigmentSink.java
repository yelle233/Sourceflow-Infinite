package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.pigment.PigmentStack;

import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 销毁机器的 Mekanism Pigment 虚空 Handler（1.20.1 Forge 版本）。
 */
public class DestructionPigmentSink implements IPigmentHandler {

    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<Long> budgetSupplier;
    private final LongConsumer budgetConsumer;

    public DestructionPigmentSink(Supplier<Boolean> canWorkSupplier,
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
    public PigmentStack getChemicalInTank(int tank) {
        return PigmentStack.EMPTY;
    }

    @Override
    public void setChemicalInTank(int tank, PigmentStack stack) {}

    @Override
    public long getTankCapacity(int tank) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isValid(int tank, PigmentStack stack) {
        return canWorkSupplier.get();
    }

    @Override
    public PigmentStack insertChemical(int tank, PigmentStack stack, Action action) {
        if (!canWorkSupplier.get() || stack.isEmpty()) return stack;
        long budget = budgetSupplier.get();
        long amt = Math.min(stack.getAmount(), budget);
        if (amt <= 0) return stack;
        if (action.execute()) budgetConsumer.accept(amt);
        long remaining = stack.getAmount() - amt;
        if (remaining <= 0) return PigmentStack.EMPTY;
        return new PigmentStack(stack.getType(), remaining);
    }

    @Override
    public PigmentStack extractChemical(int tank, long amount, Action action) {
        return PigmentStack.EMPTY;
    }

    @Override
    public PigmentStack insertChemical(PigmentStack stack, Action action) {
        return insertChemical(0, stack, action);
    }

    @Override
    public PigmentStack extractChemical(long amount, Action action) {
        return PigmentStack.EMPTY;
    }
}
