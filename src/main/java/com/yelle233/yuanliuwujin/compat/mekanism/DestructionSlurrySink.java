package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.SlurryStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 销毁机器的 Mekanism Slurry 虚空 Handler（1.20.1 Forge v2.0 版本）。
 * 接受任意浆液并转化为虚空流体，受每 tick 化学品预算限制。
 */
public class DestructionSlurrySink implements ISlurryHandler {

    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<FluidTank> voidTankSupplier;
    private final IntSupplier ratioSupplier;
    private final IntSupplier budgetSupplier;

    public DestructionSlurrySink(Supplier<Boolean> canWorkSupplier,
                                 Supplier<FluidTank> voidTankSupplier,
                                 IntSupplier ratioSupplier,
                                 IntSupplier budgetSupplier) {
        this.canWorkSupplier  = canWorkSupplier;
        this.voidTankSupplier = voidTankSupplier;
        this.ratioSupplier    = ratioSupplier;
        this.budgetSupplier   = budgetSupplier;
    }

    @Override public int getTanks() { return canWorkSupplier.get() ? 1 : 0; }
    @Override public @NotNull SlurryStack getChemicalInTank(int tank) { return SlurryStack.EMPTY; }
    @Override public void setChemicalInTank(int tank, SlurryStack stack) {}
    @Override public long getTankCapacity(int tank) {
        FluidTank vt = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        return Math.min((long)(vt.getCapacity() - vt.getFluidAmount()) * ratio, Long.MAX_VALUE);
    }
    @Override public boolean isValid(int tank, SlurryStack stack) { return canWorkSupplier.get(); }

    @Override
    public @NotNull SlurryStack insertChemical(int tank, SlurryStack stack, Action action) {
        if (!canWorkSupplier.get() || stack.isEmpty()) return stack;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        int spaceInMb = voidTank.getCapacity() - voidTank.getFluidAmount();
        long maxAccept = Math.min(Math.min((long) spaceInMb * ratio, stack.getAmount()), budgetSupplier.getAsInt());
        if (maxAccept <= 0) return stack;
        if (action.execute()) {
            int voidProduced = Math.max(1, (int)(maxAccept / ratio));
            voidTank.fill(new FluidStack(com.yelle233.yuanliuwujin.registry.ModFluids.VOID_FLUID_SOURCE.get(), voidProduced), IFluidHandler.FluidAction.EXECUTE);
        }
        long remaining = stack.getAmount() - maxAccept;
        return remaining <= 0 ? SlurryStack.EMPTY : new SlurryStack(stack.getType(), remaining);
    }

    @Override public @NotNull SlurryStack extractChemical(int tank, long amount, Action action) { return SlurryStack.EMPTY; }
    @Override public @NotNull SlurryStack insertChemical(SlurryStack stack, Action action) { return insertChemical(0, stack, action); }
    @Override public @NotNull SlurryStack extractChemical(long amount, Action action) { return SlurryStack.EMPTY; }
}

