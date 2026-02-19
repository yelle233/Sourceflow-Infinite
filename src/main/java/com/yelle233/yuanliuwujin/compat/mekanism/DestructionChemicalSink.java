package com.yelle233.yuanliuwujin.compat.mekanism;

import com.yelle233.yuanliuwujin.registry.ModFluids;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 销毁机器的化学品 Sink，按比例将化学品转换为虚空流体。
 * 与流体逻辑一致：ratio mB 化学品 → 1 mB 虚空流体，受面速率预算限制。
 */
public class DestructionChemicalSink implements IChemicalHandler {

    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<FluidTank> voidTankSupplier;
    private final IntSupplier ratioSupplier;
    /** 获取当前 tick 对应面的预算（mB 化学品）。每 tick 由 block entity 重新计算。 */
    private final IntSupplier tickBudgetSupplier;

    public DestructionChemicalSink(Supplier<Boolean> canWorkSupplier,
                                    Supplier<FluidTank> voidTankSupplier,
                                    IntSupplier ratioSupplier,
                                    IntSupplier tickBudgetSupplier) {
        this.canWorkSupplier = canWorkSupplier;
        this.voidTankSupplier = voidTankSupplier;
        this.ratioSupplier = ratioSupplier;
        this.tickBudgetSupplier = tickBudgetSupplier;
    }

    @Override public int getChemicalTanks() { return canWorkSupplier.get() ? 1 : 0; }
    @Override public ChemicalStack getChemicalInTank(int tank) { return ChemicalStack.EMPTY; }
    @Override public void setChemicalInTank(int tank, ChemicalStack stack) {}
    @Override public long getChemicalTankCapacity(int tank) { return Long.MAX_VALUE; }
    @Override public boolean isValid(int tank, ChemicalStack stack) { return canWorkSupplier.get(); }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        if (!canWorkSupplier.get() || stack.isEmpty()) return stack;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());

        // 虚空储罐可容纳量
        int voidSpace = voidTank.getCapacity() - voidTank.getFluidAmount();
        // 受面速率预算限制
        int budget = tickBudgetSupplier.getAsInt();
        // 最大可接受化学品量 = min(stack, voidSpace * ratio, budget)
        long maxAccept = Math.min(stack.getAmount(), Math.min((long) voidSpace * ratio, budget));
        if (maxAccept <= 0) return stack;

        int voidProduced = (int) Math.max(1, maxAccept / ratio);
        if (action.execute()) {
            FluidStack voidFluid = new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), voidProduced);
            voidTank.fill(voidFluid, IFluidHandler.FluidAction.EXECUTE);
        }

        long remaining = stack.getAmount() - maxAccept;
        if (remaining <= 0) return ChemicalStack.EMPTY;
        return stack.getChemical().getStack(remaining);
    }

    @Override public ChemicalStack extractChemical(int tank, long amount, Action action) { return ChemicalStack.EMPTY; }
    @Override public ChemicalStack insertChemical(ChemicalStack stack, Action action) { return insertChemical(0, stack, action); }
    @Override public ChemicalStack extractChemical(long amount, Action action) { return ChemicalStack.EMPTY; }
}
