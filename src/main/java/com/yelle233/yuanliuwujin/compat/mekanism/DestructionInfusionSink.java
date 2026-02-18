package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.InfusionStack;

import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 销毁机器的 Mekanism Infusion 虚空 Handler（1.20.1 Forge 版本）。
 * <p>
 * 接受任意灌注类型并将其虚空销毁，受每 tick 预算限制。
 */
public class DestructionInfusionSink implements IInfusionHandler {

    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<Long> budgetSupplier;
    private final LongConsumer budgetConsumer;

    public DestructionInfusionSink(Supplier<Boolean> canWorkSupplier,
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
    public InfusionStack getChemicalInTank(int tank) {
        return InfusionStack.EMPTY;
    }

    @Override
    public void setChemicalInTank(int tank, InfusionStack stack) {}

    @Override
    public long getTankCapacity(int tank) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isValid(int tank, InfusionStack stack) {
        return canWorkSupplier.get();
    }

    @Override
    public InfusionStack insertChemical(int tank, InfusionStack stack, Action action) {
        if (!canWorkSupplier.get() || stack.isEmpty()) return stack;
        long budget = budgetSupplier.get();
        long amt = Math.min(stack.getAmount(), budget);
        if (amt <= 0) return stack;
        if (action.execute()) budgetConsumer.accept(amt);
        long remaining = stack.getAmount() - amt;
        if (remaining <= 0) return InfusionStack.EMPTY;
        return new InfusionStack(stack.getType(), remaining);
    }

    @Override
    public InfusionStack extractChemical(int tank, long amount, Action action) {
        return InfusionStack.EMPTY;
    }

    @Override
    public InfusionStack insertChemical(InfusionStack stack, Action action) {
        return insertChemical(0, stack, action);
    }

    @Override
    public InfusionStack extractChemical(long amount, Action action) {
        return InfusionStack.EMPTY;
    }
}
