package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 无限颜料输出：只能被动抽取（extract），不能插入（insert）
 * - typeSupplier: 当前绑定的 Pigment（可能为 null）
 * - canWorkSupplier: 机器是否允许工作（例如有核心、未红石停机等）
 * - budgetSupplier/budgetConsumer: 本 tick 可被抽取的最大额度控制
 */
public class InfinitePigmentOutput implements IPigmentHandler {

    private final Supplier<Pigment> typeSupplier;
    private final Supplier<Boolean> canWorkSupplier;
    private final Supplier<Long> budgetSupplier;
    private final LongConsumer budgetConsumer;

    public InfinitePigmentOutput(Supplier<Pigment> typeSupplier,
                                 Supplier<Boolean> canWorkSupplier,
                                 Supplier<Long> budgetSupplier,
                                 LongConsumer budgetConsumer) {
        this.typeSupplier = typeSupplier;
        this.canWorkSupplier = canWorkSupplier;
        this.budgetSupplier = budgetSupplier;
        this.budgetConsumer = budgetConsumer;
    }

    private Pigment getActive() {
        if (!canWorkSupplier.get()) return null;
        return typeSupplier.get();
    }

    @Override
    public int getTanks() {
        return getActive() != null ? 1 : 0;
    }

    @Override
    public @NotNull PigmentStack getChemicalInTank(int tank) {
        Pigment t = getActive();
        return t != null ? new PigmentStack(t, Long.MAX_VALUE) : PigmentStack.EMPTY;
    }

    @Override
    public void setChemicalInTank(int tank, PigmentStack stack) {
        // 只输出，不允许外部设置
    }

    @Override
    public long getTankCapacity(int tank) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isValid(int tank, PigmentStack stack) {
        // 不允许插入
        return false;
    }

    // --- 插入：全部拒绝，原样返回（表示没插入进去） ---
    @Override
    public @NotNull PigmentStack insertChemical(int tank, PigmentStack stack, Action action) {
        return stack;
    }

    @Override
    public @NotNull PigmentStack insertChemical(PigmentStack stack, Action action) {
        return stack;
    }

    // --- 抽取：受预算限制 ---
    @Override
    public @NotNull PigmentStack extractChemical(int tank, long amount, Action action) {
        Pigment t = getActive();
        if (t == null || amount <= 0) return PigmentStack.EMPTY;

        long budget = budgetSupplier.get();
        long toGive = Math.min(amount, budget);
        if (toGive <= 0) return PigmentStack.EMPTY;

        if (action.execute()) {
            budgetConsumer.accept(toGive);
        }
        return new PigmentStack(t, toGive);
    }

    @Override
    public @NotNull PigmentStack extractChemical(long amount, Action action) {
        return extractChemical(0, amount, action);
    }
}
