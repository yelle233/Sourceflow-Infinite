package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;

import java.util.function.Supplier;

/**
 * 无限 Gas 输出 Handler（1.20.1 Forge 版本）。
 * <p>
 * Mekanism 10.4.x 使用分离的化学品类型（Gas/InfuseType/Pigment/Slurry），
 * 此类实现 {@link IGasHandler}（而非后续版本的统一 IChemicalHandler）。
 */
public class InfiniteGasOutput implements IGasHandler {

    private final Supplier<Gas> gasSupplier;
    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<Long> budgetSupplier;
    private final java.util.function.LongConsumer budgetConsumer;

    public InfiniteGasOutput(Supplier<Gas> gasSupplier,
                             Supplier<Boolean> canWorkSupplier,
                             Supplier<Long> budgetSupplier,
                             java.util.function.LongConsumer budgetConsumer) {
        this.gasSupplier = gasSupplier;
        this.canWorkSupplier = canWorkSupplier;
        this.budgetSupplier = budgetSupplier;
        this.budgetConsumer = budgetConsumer;
    }

    private Gas getActiveGas() {
        if (!canWorkSupplier.get()) return null;
        return gasSupplier.get();
    }

    @Override
    public int getTanks() {
        return getActiveGas() != null ? 1 : 0;
    }

    @Override
    public GasStack getChemicalInTank(int tank) {
        Gas g = getActiveGas();
        return g != null ? new GasStack(g, Long.MAX_VALUE) : GasStack.EMPTY;
    }

    @Override
    public void setChemicalInTank(int tank, GasStack stack) {
        // 无限输出，不接受设置
    }

    @Override
    public long getTankCapacity(int tank) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isValid(int tank, GasStack stack) {
        return false;
    }

    @Override
    public GasStack insertChemical(int tank, GasStack stack, Action action) {
        return stack; // 全部退回
    }

    @Override
    public GasStack extractChemical(int tank, long amount, Action action) {
        Gas g = getActiveGas();
        if (g == null || amount <= 0) return GasStack.EMPTY;

        long budget = budgetSupplier.get();
        long amt = Math.min(amount, budget);
        if (amt <= 0) return GasStack.EMPTY;

        if (action.execute()) {
            budgetConsumer.accept(amt);
        }
        return new GasStack(g, amt);
    }

    @Override
    public GasStack insertChemical(GasStack stack, Action action) {
        return stack; // 全部退回
    }

    @Override
    public GasStack extractChemical(long amount, Action action) {
        Gas g = getActiveGas();
        if (g == null || amount <= 0) return GasStack.EMPTY;

        long budget = budgetSupplier.get();
        long amt = Math.min(amount, budget);
        if (amt <= 0) return GasStack.EMPTY;

        if (action.execute()) {
            budgetConsumer.accept(amt);
        }
        return new GasStack(g, amt);
    }
}
