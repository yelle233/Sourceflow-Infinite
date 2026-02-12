package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;

import java.util.function.Supplier;

/**
 * 无限化学品输出 Handler。
 * <p>
 * 类似于流体系统的 {@code infiniteOutput}，此 Handler 向外暴露无限量的指定化学品。
 * 不接受外部插入（insertChemical 总是返回输入原样），仅支持提取（extractChemical）。
 * <p>
 * 通过 {@code chemicalSupplier} 动态获取当前绑定的化学品，
 * 通过 {@code canWorkSupplier} 判断机器是否处于可工作状态。
 */
public class InfiniteChemicalOutput implements IChemicalHandler {

    /**
     * 动态获取当前绑定的 Chemical（可能为 null）。
     * 注意：javax.annotation.Nullable 不支持 TYPE_USE，
     * 所以不在泛型参数上标注，而是在使用时做 null 检查。
     */
    private final Supplier<Chemical> chemicalSupplier;

    /** 判断机器是否可以工作（有电+有核心+未被ban） */
    private final Supplier<Boolean> canWorkSupplier;

    private final Supplier<Long> budgetSupplier;

    private final java.util.function.LongConsumer budgetConsumer;

    /**
     * @param chemicalSupplier 提供当前绑定化学品的 Supplier（如果返回 null 则停止输出）
     * @param canWorkSupplier  判断机器是否可工作
     */
    public InfiniteChemicalOutput(Supplier<Chemical> chemicalSupplier,
                                  Supplier<Boolean> canWorkSupplier,
                                  Supplier<Long> budgetSupplier,
                                  java.util.function.LongConsumer budgetConsumer) {
        this.chemicalSupplier = chemicalSupplier;
        this.canWorkSupplier = canWorkSupplier;
        this.budgetSupplier = budgetSupplier;
        this.budgetConsumer = budgetConsumer;
    }

    /**
     * 获取当前可输出的化学品，如果不可工作则返回 null。
     */
    private Chemical getActiveChemical() {
        if (!canWorkSupplier.get()) return null;
        return chemicalSupplier.get();
    }

    @Override
    public int getChemicalTanks() {
        return getActiveChemical() != null ? 1 : 0;
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        Chemical c = getActiveChemical();
        return c != null ? c.getStack(Long.MAX_VALUE) : ChemicalStack.EMPTY;
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {
        // 无限输出，不接受设置
    }

    // 10.7.11+ 从 getTankCapacity 重命名为 getChemicalTankCapacity，避免与 IFluidHandler 方法冲突
    @Override
    public long getChemicalTankCapacity(int tank) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return false; // 不接受外部插入
    }

    /**
     * 插入化学品 —— 不接受，返回原样（即全部为 remainder）。
     */
    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        return stack; // 全部退回
    }

    /**
     * 提取化学品 —— 无限量提供。
     */
    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        Chemical c = getActiveChemical();
        if (c == null || amount <= 0) return ChemicalStack.EMPTY;

        long budget = budgetSupplier.get();
        long amt = Math.min(amount, budget);
        if (amt <= 0) return ChemicalStack.EMPTY;

        if (action.execute()) {
            budgetConsumer.accept(amt);
        }
        return c.getStack(amt);
    }

    @Override
    public ChemicalStack insertChemical(ChemicalStack stack, Action action) {
        return stack; // 全部退回
    }

    @Override
    public ChemicalStack extractChemical(long amount, Action action) {
        Chemical c = getActiveChemical();
        if (c == null || amount <= 0) return ChemicalStack.EMPTY;

        long budget = budgetSupplier.get();
        long amt = Math.min(amount, budget);
        if (amt <= 0) return ChemicalStack.EMPTY;

        if (action.execute()) {
            budgetConsumer.accept(amt);
        }
        return c.getStack(amt);
    }
}
