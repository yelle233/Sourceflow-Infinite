package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 无限灌注输出 Handler（1.20.1 Forge v2.0 版本）。
 * 消耗虚空流体产出绑定的 InfuseType，受面速率预算限制。
 */
public class InfiniteInfusionOutput implements IInfusionHandler {

    private final Supplier<InfuseType> typeSupplier;
    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<FluidTank> voidTankSupplier;
    private final IntSupplier ratioSupplier;
    private final IntSupplier budgetSupplier;
    private final IntConsumer budgetConsumer;

    public InfiniteInfusionOutput(Supplier<InfuseType> typeSupplier,
                                  Supplier<Boolean> canWorkSupplier,
                                  Supplier<FluidTank> voidTankSupplier,
                                  IntSupplier ratioSupplier,
                                  IntSupplier budgetSupplier,
                                  IntConsumer budgetConsumer) {
        this.typeSupplier     = typeSupplier;
        this.canWorkSupplier  = canWorkSupplier;
        this.voidTankSupplier = voidTankSupplier;
        this.ratioSupplier    = ratioSupplier;
        this.budgetSupplier   = budgetSupplier;
        this.budgetConsumer   = budgetConsumer;
    }

    private InfuseType getActive() {
        if (!canWorkSupplier.get()) return null;
        return typeSupplier.get();
    }

    @Override public int getTanks() { return getActive() != null ? 1 : 0; }

    @Override
    public @NotNull InfusionStack getChemicalInTank(int tank) {
        InfuseType t = getActive();
        if (t == null) return InfusionStack.EMPTY;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        long available = Math.min(voidTank.getFluidAmount() / (long) ratio, budgetSupplier.getAsInt());
        return available > 0 ? new InfusionStack(t, available) : InfusionStack.EMPTY;
    }

    @Override public void setChemicalInTank(int tank, InfusionStack stack) {}
    @Override public long getTankCapacity(int tank) { return Long.MAX_VALUE; }
    @Override public boolean isValid(int tank, InfusionStack stack) { return false; }
    @Override public @NotNull InfusionStack insertChemical(int tank, InfusionStack stack, Action action) { return stack; }
    @Override public @NotNull InfusionStack insertChemical(InfusionStack stack, Action action) { return stack; }

    @Override
    public @NotNull InfusionStack extractChemical(int tank, long amount, Action action) {
        InfuseType t = getActive();
        if (t == null || amount <= 0) return InfusionStack.EMPTY;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        long actual = Math.min(amount, Math.min(voidTank.getFluidAmount() / (long) ratio, budgetSupplier.getAsInt()));
        if (actual <= 0) return InfusionStack.EMPTY;
        if (action.execute()) {
            voidTank.drain((int)(actual * ratio), IFluidHandler.FluidAction.EXECUTE);
            budgetConsumer.accept((int) actual);
        }
        return new InfusionStack(t, actual);
    }

    @Override
    public @NotNull InfusionStack extractChemical(long amount, Action action) {
        return extractChemical(0, amount, action);
    }
}

