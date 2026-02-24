package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 无限颜料输出 Handler（1.20.1 Forge v2.0 版本）。
 * 消耗虚空流体产出绑定的 Pigment，受面速率预算限制。
 */
public class InfinitePigmentOutput implements IPigmentHandler {

    private final Supplier<Pigment> typeSupplier;
    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<FluidTank> voidTankSupplier;
    private final IntSupplier ratioSupplier;
    private final IntSupplier budgetSupplier;
    private final IntConsumer budgetConsumer;

    public InfinitePigmentOutput(Supplier<Pigment> typeSupplier,
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

    private Pigment getActive() {
        if (!canWorkSupplier.get()) return null;
        return typeSupplier.get();
    }

    @Override public int getTanks() { return getActive() != null ? 1 : 0; }

    @Override
    public @NotNull PigmentStack getChemicalInTank(int tank) {
        Pigment p = getActive();
        if (p == null) return PigmentStack.EMPTY;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        long available = Math.min(voidTank.getFluidAmount() / (long) ratio, budgetSupplier.getAsInt());
        return available > 0 ? new PigmentStack(p, available) : PigmentStack.EMPTY;
    }

    @Override public void setChemicalInTank(int tank, PigmentStack stack) {}
    @Override public long getTankCapacity(int tank) { return Long.MAX_VALUE; }
    @Override public boolean isValid(int tank, PigmentStack stack) { return false; }
    @Override public @NotNull PigmentStack insertChemical(int tank, PigmentStack stack, Action action) { return stack; }
    @Override public @NotNull PigmentStack insertChemical(PigmentStack stack, Action action) { return stack; }

    @Override
    public @NotNull PigmentStack extractChemical(int tank, long amount, Action action) {
        Pigment p = getActive();
        if (p == null || amount <= 0) return PigmentStack.EMPTY;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        long actual = Math.min(amount, Math.min(voidTank.getFluidAmount() / (long) ratio, budgetSupplier.getAsInt()));
        if (actual <= 0) return PigmentStack.EMPTY;
        if (action.execute()) {
            voidTank.drain((int)(actual * ratio), IFluidHandler.FluidAction.EXECUTE);
            budgetConsumer.accept((int) actual);
        }
        return new PigmentStack(p, actual);
    }

    @Override
    public @NotNull PigmentStack extractChemical(long amount, Action action) {
        return extractChemical(0, amount, action);
    }
}

