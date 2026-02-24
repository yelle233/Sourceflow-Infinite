package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 无限 Gas 输出 Handler（1.20.1 Forge v2.0 版本）。
 * <p>
 * 消耗虚空流体产出绑定的 Gas：ratio mB 虚空流体 → 1 mB Gas，受面速率预算限制。
 */
public class InfiniteGasOutput implements IGasHandler {

    private final Supplier<Gas> gasSupplier;
    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<FluidTank> voidTankSupplier;
    private final IntSupplier ratioSupplier;
    private final IntSupplier budgetSupplier;
    private final IntConsumer budgetConsumer;

    public InfiniteGasOutput(Supplier<Gas> gasSupplier,
                             Supplier<Boolean> canWorkSupplier,
                             Supplier<FluidTank> voidTankSupplier,
                             IntSupplier ratioSupplier,
                             IntSupplier budgetSupplier,
                             IntConsumer budgetConsumer) {
        this.gasSupplier      = gasSupplier;
        this.canWorkSupplier  = canWorkSupplier;
        this.voidTankSupplier = voidTankSupplier;
        this.ratioSupplier    = ratioSupplier;
        this.budgetSupplier   = budgetSupplier;
        this.budgetConsumer   = budgetConsumer;
    }

    private Gas getActiveGas() {
        if (!canWorkSupplier.get()) return null;
        return gasSupplier.get();
    }

    @Override public int getTanks() { return getActiveGas() != null ? 1 : 0; }

    @Override
    public GasStack getChemicalInTank(int tank) {
        Gas g = getActiveGas();
        if (g == null) return GasStack.EMPTY;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        long available = Math.min(voidTank.getFluidAmount() / (long) ratio, budgetSupplier.getAsInt());
        return available > 0 ? new GasStack(g, available) : GasStack.EMPTY;
    }

    @Override public void setChemicalInTank(int tank, GasStack stack) {}
    @Override public long getTankCapacity(int tank) { return Long.MAX_VALUE; }
    @Override public boolean isValid(int tank, GasStack stack) { return false; }
    @Override public GasStack insertChemical(int tank, GasStack stack, Action action) { return stack; }
    @Override public GasStack insertChemical(GasStack stack, Action action) { return stack; }

    @Override
    public GasStack extractChemical(int tank, long amount, Action action) {
        Gas g = getActiveGas();
        if (g == null || amount <= 0) return GasStack.EMPTY;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        long maxByVoid = voidTank.getFluidAmount() / (long) ratio;
        long actual = Math.min(amount, Math.min(maxByVoid, budgetSupplier.getAsInt()));
        if (actual <= 0) return GasStack.EMPTY;
        if (action.execute()) {
            voidTank.drain((int)(actual * ratio), IFluidHandler.FluidAction.EXECUTE);
            budgetConsumer.accept((int) actual);
        }
        return new GasStack(g, actual);
    }

    @Override
    public GasStack extractChemical(long amount, Action action) {
        return extractChemical(0, amount, action);
    }
}

