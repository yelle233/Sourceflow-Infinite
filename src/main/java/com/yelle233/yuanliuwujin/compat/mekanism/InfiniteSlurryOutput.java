package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 无限浆液输出 Handler（1.20.1 Forge v2.0 版本）。
 * 消耗虚空流体产出绑定的 Slurry，受面速率预算限制。
 */
public class InfiniteSlurryOutput implements ISlurryHandler {

    private final Supplier<Slurry> typeSupplier;
    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<FluidTank> voidTankSupplier;
    private final IntSupplier ratioSupplier;
    private final IntSupplier budgetSupplier;
    private final IntConsumer budgetConsumer;

    public InfiniteSlurryOutput(Supplier<Slurry> typeSupplier,
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

    private Slurry getActive() {
        if (!canWorkSupplier.get()) return null;
        return typeSupplier.get();
    }

    @Override public int getTanks() { return getActive() != null ? 1 : 0; }

    @Override
    public @NotNull SlurryStack getChemicalInTank(int tank) {
        Slurry s = getActive();
        if (s == null) return SlurryStack.EMPTY;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        long available = Math.min(voidTank.getFluidAmount() / (long) ratio, budgetSupplier.getAsInt());
        return available > 0 ? new SlurryStack(s, available) : SlurryStack.EMPTY;
    }

    @Override public void setChemicalInTank(int tank, SlurryStack stack) {}
    @Override public long getTankCapacity(int tank) { return Long.MAX_VALUE; }
    @Override public boolean isValid(int tank, SlurryStack stack) { return false; }
    @Override public @NotNull SlurryStack insertChemical(int tank, SlurryStack stack, Action action) { return stack; }
    @Override public @NotNull SlurryStack insertChemical(SlurryStack stack, Action action) { return stack; }

    @Override
    public @NotNull SlurryStack extractChemical(int tank, long amount, Action action) {
        Slurry s = getActive();
        if (s == null || amount <= 0) return SlurryStack.EMPTY;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        long actual = Math.min(amount, Math.min(voidTank.getFluidAmount() / (long) ratio, budgetSupplier.getAsInt()));
        if (actual <= 0) return SlurryStack.EMPTY;
        if (action.execute()) {
            voidTank.drain((int)(actual * ratio), IFluidHandler.FluidAction.EXECUTE);
            budgetConsumer.accept((int) actual);
        }
        return new SlurryStack(s, actual);
    }

    @Override
    public @NotNull SlurryStack extractChemical(long amount, Action action) {
        return extractChemical(0, amount, action);
    }
}

