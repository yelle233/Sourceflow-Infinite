package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 无限浆液输出：只能被动抽取（extract），不能插入（insert）
 * - typeSupplier: 当前绑定的 Slurry（可能为 null）
 * - canWorkSupplier: 机器是否允许工作
 * - budgetSupplier/budgetConsumer: 本 tick 可被抽取的最大额度控制
 */
public class InfiniteSlurryOutput implements ISlurryHandler {

    private final Supplier<Slurry> typeSupplier;
    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<Long> budgetSupplier;
    private final LongConsumer budgetConsumer;

    public InfiniteSlurryOutput(Supplier<Slurry> typeSupplier,
                                Supplier<Boolean> canWorkSupplier,
                                Supplier<Long> budgetSupplier,
                                LongConsumer budgetConsumer) {
        this.typeSupplier = typeSupplier;
        this.canWorkSupplier = canWorkSupplier;
        this.budgetSupplier = budgetSupplier;
        this.budgetConsumer = budgetConsumer;
    }

    private Slurry getActive() {
        if (!canWorkSupplier.get()) return null;
        return typeSupplier.get();
    }

    @Override
    public int getTanks() {
        return getActive() != null ? 1 : 0;
    }

    @Override
    public @NotNull SlurryStack getChemicalInTank(int tank) {
        Slurry t = getActive();
        return t != null ? new SlurryStack(t, Long.MAX_VALUE) : SlurryStack.EMPTY;
    }

    @Override
    public void setChemicalInTank(int tank, SlurryStack stack) {
        // 只输出，不允许外部设置
    }

    @Override
    public long getTankCapacity(int tank) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isValid(int tank, SlurryStack stack) {
        return false;
    }

    // --- 插入：全部拒绝 ---
    @Override
    public @NotNull SlurryStack insertChemical(int tank, SlurryStack stack, Action action) {
        return stack;
    }

    @Override
    public @NotNull SlurryStack insertChemical(SlurryStack stack, Action action) {
        return stack;
    }

    // --- 抽取：受预算限制 ---
    @Override
    public @NotNull SlurryStack extractChemical(int tank, long amount, Action action) {
        Slurry t = getActive();
        if (t == null || amount <= 0) return SlurryStack.EMPTY;

        long budget = budgetSupplier.get();
        long toGive = Math.min(amount, budget);
        if (toGive <= 0) return SlurryStack.EMPTY;

        if (action.execute()) {
            budgetConsumer.accept(toGive);
        }
        return new SlurryStack(t, toGive);
    }

    @Override
    public @NotNull SlurryStack extractChemical(long amount, Action action) {
        return extractChemical(0, amount, action);
    }
}
