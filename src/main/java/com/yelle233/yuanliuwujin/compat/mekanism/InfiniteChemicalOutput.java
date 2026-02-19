package com.yelle233.yuanliuwujin.compat.mekanism;

import com.yelle233.yuanliuwujin.registry.ModFluids;
import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 无限化学品输出 Handler，消耗虚空流体产出绑定化学品。
 * 与流体输出逻辑一致：ratio mB 虚空流体 → 1 mB 化学品，受面速率预算限制。
 */
public class InfiniteChemicalOutput implements IChemicalHandler {

    private final Supplier<Chemical> chemicalSupplier;
    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<FluidTank> voidTankSupplier;
    private final IntSupplier ratioSupplier;
    /** 获取当前 tick 剩余预算（mB 化学品） */
    private final IntSupplier budgetSupplier;
    /** 消耗预算 */
    private final IntConsumer budgetConsumer;

    public InfiniteChemicalOutput(Supplier<Chemical> chemicalSupplier,
                                  Supplier<Boolean> canWorkSupplier,
                                  Supplier<FluidTank> voidTankSupplier,
                                  IntSupplier ratioSupplier,
                                  IntSupplier budgetSupplier,
                                  IntConsumer budgetConsumer) {
        this.chemicalSupplier = chemicalSupplier;
        this.canWorkSupplier = canWorkSupplier;
        this.voidTankSupplier = voidTankSupplier;
        this.ratioSupplier = ratioSupplier;
        this.budgetSupplier = budgetSupplier;
        this.budgetConsumer = budgetConsumer;
    }

    private Chemical getActiveChemical() {
        if (!canWorkSupplier.get()) return null;
        return chemicalSupplier.get();
    }

    @Override public int getChemicalTanks() { return getActiveChemical() != null ? 1 : 0; }

    @Override public ChemicalStack getChemicalInTank(int tank) {
        Chemical c = getActiveChemical();
        if (c == null) return ChemicalStack.EMPTY;
        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());
        long available = voidTank.getFluidAmount() / (long) ratio;
        // 也受预算限制
        available = Math.min(available, budgetSupplier.getAsInt());
        return available > 0 ? c.getStack(available) : ChemicalStack.EMPTY;
    }

    @Override public void setChemicalInTank(int tank, ChemicalStack stack) {}
    @Override public long getChemicalTankCapacity(int tank) { return Long.MAX_VALUE; }
    @Override public boolean isValid(int tank, ChemicalStack stack) { return false; }
    @Override public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) { return stack; }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        Chemical c = getActiveChemical();
        if (c == null || amount <= 0) return ChemicalStack.EMPTY;

        FluidTank voidTank = voidTankSupplier.get();
        int ratio = Math.max(1, ratioSupplier.getAsInt());

        // 受虚空储量限制
        long maxByVoid = voidTank.getFluidAmount() / (long) ratio;
        // 受预算限制
        int budget = budgetSupplier.getAsInt();
        long actualAmount = Math.min(amount, Math.min(maxByVoid, budget));
        if (actualAmount <= 0) return ChemicalStack.EMPTY;

        if (action.execute()) {
            int voidConsumed = (int) (actualAmount * ratio);
            voidTank.drain(voidConsumed, IFluidHandler.FluidAction.EXECUTE);
            budgetConsumer.accept((int) actualAmount);
        }
        return c.getStack(actualAmount);
    }

    @Override public ChemicalStack insertChemical(ChemicalStack stack, Action action) { return stack; }
    @Override public ChemicalStack extractChemical(long amount, Action action) {
        return extractChemical(0, amount, action);
    }
}
