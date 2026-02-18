package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;

import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 销毁机器的 Mekanism Gas 虚空 Handler（1.20.1 Forge 版本）。
 * <p>
 * 接受任意 Gas 并将其虚空销毁（不存储），受每 tick 预算限制。
 * <p>
 * <b>重要</b>：此类引用 Mekanism API，仅在 Mekanism 已加载时实例化！
 */
public class DestructionGasSink implements IGasHandler {

    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<Long> budgetSupplier;
    private final LongConsumer budgetConsumer;

    public DestructionGasSink(Supplier<Boolean> canWorkSupplier,
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
    public GasStack getChemicalInTank(int tank) {
        return GasStack.EMPTY;
    }

    @Override
    public void setChemicalInTank(int tank, GasStack stack) {
        // 虚空槽不接受外部设置
    }

    @Override
    public long getTankCapacity(int tank) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isValid(int tank, GasStack stack) {
        return canWorkSupplier.get();
    }

    @Override
    public GasStack insertChemical(int tank, GasStack stack, Action action) {
        if (!canWorkSupplier.get() || stack.isEmpty()) return stack;
        long budget = budgetSupplier.get();
        long amt = Math.min(stack.getAmount(), budget);
        if (amt <= 0) return stack;
        if (action.execute()) budgetConsumer.accept(amt);
        long remaining = stack.getAmount() - amt;
        if (remaining <= 0) return GasStack.EMPTY;
        return new GasStack(stack.getType(), remaining);
    }

    @Override
    public GasStack extractChemical(int tank, long amount, Action action) {
        return GasStack.EMPTY;
    }

    @Override
    public GasStack insertChemical(GasStack stack, Action action) {
        return insertChemical(0, stack, action);
    }

    @Override
    public GasStack extractChemical(long amount, Action action) {
        return GasStack.EMPTY;
    }
}
