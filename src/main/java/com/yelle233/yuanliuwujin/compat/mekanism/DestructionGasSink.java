package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 销毁机器的 Mekanism Gas 虚空 Handler（1.20.1 Forge v2.0 版本）。
 * <p>
 * 接受任意 Gas 并将其转化为虚空流体存入虚空储罐，受每 tick 化学品预算限制。
 * 转换比：1 mB Gas → 1/ratio mB 虚空流体（化学品 × ratio → 1 mB 虚空流体）。
 * <p>
 * <b>重要</b>：此类引用 Mekanism API，仅在 Mekanism 已加载时实例化！
 */
public class DestructionGasSink implements IGasHandler {

    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<FluidTank> voidTankSupplier;
    private final IntSupplier ratioSupplier;
    private final IntSupplier budgetSupplier;

    public DestructionGasSink(Supplier<Boolean> canWorkSupplier,
                              Supplier<FluidTank> voidTankSupplier,
                              IntSupplier ratioSupplier,
                              IntSupplier budgetSupplier) {
        this.canWorkSupplier  = canWorkSupplier;
        this.voidTankSupplier = voidTankSupplier;
        this.ratioSupplier    = ratioSupplier;
        this.budgetSupplier   = budgetSupplier;
    }

    @Override public int getTanks() { return canWorkSupplier.get() ? 1 : 0; }
    @Override public GasStack getChemicalInTank(int tank) { return GasStack.EMPTY; }
    @Override public void setChemicalInTank(int tank, GasStack stack) {}
    @Override public long getTankCapacity(int tank) {
        FluidTank vt = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        long space = (long)(vt.getCapacity() - vt.getFluidAmount()) * ratio;
        return Math.min(space, Long.MAX_VALUE);
    }
    @Override public boolean isValid(int tank, GasStack stack) { return canWorkSupplier.get(); }

    @Override
    public GasStack insertChemical(int tank, GasStack stack, Action action) {
        if (!canWorkSupplier.get() || stack.isEmpty()) return stack;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        int spaceInMb = voidTank.getCapacity() - voidTank.getFluidAmount();
        // 化学品消耗量受：虚空罐剩余空间（×ratio）和预算 两个限制
        long maxAccept = Math.min(Math.min((long) spaceInMb * ratio, stack.getAmount()), budgetSupplier.getAsInt());
        if (maxAccept <= 0) return stack;
        if (action.execute()) {
            int voidProduced = Math.max(1, (int)(maxAccept / ratio));
            FluidStack voidFluid = new FluidStack(com.yelle233.yuanliuwujin.registry.ModFluids.VOID_FLUID_SOURCE.get(), voidProduced);
            voidTank.fill(voidFluid, IFluidHandler.FluidAction.EXECUTE);
        }
        long remaining = stack.getAmount() - maxAccept;
        return remaining <= 0 ? GasStack.EMPTY : new GasStack(stack.getType(), remaining);
    }

    @Override public GasStack extractChemical(int tank, long amount, Action action) { return GasStack.EMPTY; }
    @Override public GasStack insertChemical(GasStack stack, Action action) { return insertChemical(0, stack, action); }
    @Override public GasStack extractChemical(long amount, Action action) { return GasStack.EMPTY; }
}

