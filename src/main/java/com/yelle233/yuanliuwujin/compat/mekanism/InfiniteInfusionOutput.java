package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;
/**
 * 无限灌注输出：只能被动抽取（extract），不能插入（insert）
 * - typeSupplier: 当前绑定的 Infusion（可能为 null）
 * - canWorkSupplier: 机器是否允许工作（例如有核心、未红石停机等）
 * - budgetSupplier/budgetConsumer: 本 tick 可被抽取的最大额度控制
 */
public class InfiniteInfusionOutput implements IInfusionHandler {
    private final Supplier<InfuseType> typeSupplier;
    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<Long> budgetSupplier;
    private final java.util.function.LongConsumer budgetConsumer;

    public InfiniteInfusionOutput(Supplier<InfuseType> typeSupplier,
                                  Supplier<Boolean> canWorkSupplier,
                                  Supplier<Long> budgetSupplier,
                                  java.util.function.LongConsumer budgetConsumer) {
        this.typeSupplier = typeSupplier;
        this.canWorkSupplier = canWorkSupplier;
        this.budgetSupplier = budgetSupplier;
        this.budgetConsumer = budgetConsumer;
    }

    private InfuseType getActive() {
        if (!canWorkSupplier.get()) return null;
        return typeSupplier.get();
    }

    @Override public int getTanks() { return getActive() != null ? 1 : 0; }

    @Override public InfusionStack getChemicalInTank(int tank) {
        InfuseType t = getActive();
        return t != null ? new InfusionStack(t, Long.MAX_VALUE) : InfusionStack.EMPTY;
    }

    @Override public void setChemicalInTank(int tank, InfusionStack stack) {}

    @Override public long getTankCapacity(int tank) { return Long.MAX_VALUE; }

    @Override public boolean isValid(int tank, InfusionStack stack) { return false; }

    @Override public @NotNull InfusionStack insertChemical(int tank, InfusionStack stack, Action action) { return stack; }

    @Override public @NotNull InfusionStack extractChemical(int tank, long amount, Action action) {
        InfuseType t = getActive();
        if (t == null || amount <= 0) return InfusionStack.EMPTY;

        long budget = budgetSupplier.get();
        long amt = Math.min(amount, budget);
        if (amt <= 0) return InfusionStack.EMPTY;

        if (action.execute()) budgetConsumer.accept(amt);
        return new InfusionStack(t, amt);
    }

    @Override public @NotNull InfusionStack insertChemical(InfusionStack stack, Action action) { return stack; }

    @Override public @NotNull InfusionStack extractChemical(long amount, Action action) {
        return extractChemical(0, amount, action);
    }
}
