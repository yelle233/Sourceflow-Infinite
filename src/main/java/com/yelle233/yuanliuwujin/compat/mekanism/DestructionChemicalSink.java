package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;

import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 销毁机器的 Mekanism 化学品虚空 Handler。
 * <p>
 * 与 {@link InfiniteChemicalOutput} 的逻辑相反：
 * <ul>
 *   <li>{@code insertChemical()} - 接受并销毁化学品（受每 tick 预算限制），返回未被接受的剩余量</li>
 *   <li>{@code extractChemical()} - 始终返回空（虚空槽不提供化学品）</li>
 * </ul>
 * <p>
 * <b>重要</b>：此类引用 Mekanism API，仅在 Mekanism 已加载时实例化！
 */
public class DestructionChemicalSink implements IChemicalHandler {

    /** 判断机器是否处于可工作状态（有核心 + 有电） */
    private final Supplier<Boolean> canWorkSupplier;

    /** 获取当前 tick 剩余可销毁化学品量 */
    private final Supplier<Long> budgetSupplier;

    /** 通知已消耗的化学品量，用于扣减预算 */
    private final LongConsumer budgetConsumer;

    public DestructionChemicalSink(Supplier<Boolean> canWorkSupplier,
                                    Supplier<Long> budgetSupplier,
                                    LongConsumer budgetConsumer) {
        this.canWorkSupplier = canWorkSupplier;
        this.budgetSupplier  = budgetSupplier;
        this.budgetConsumer  = budgetConsumer;
    }

    @Override
    public int getChemicalTanks() {
        // 不可工作时暴露 0 个槽，管道会断开
        return canWorkSupplier.get() ? 1 : 0;
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        return ChemicalStack.EMPTY; // 虚空槽不存储化学品
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {
        // 不接受外部设置
    }

    @Override
    public long getChemicalTankCapacity(int tank) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return canWorkSupplier.get(); // 运行时接受任意化学品
    }

    /**
     * 单槽插入入口（由 Mekanism 内部调用）。
     * 接受化学品并销毁，返回未被接受的剩余量（ChemicalStack.EMPTY 表示全部被接受）。
     */
    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        if (!canWorkSupplier.get() || stack.isEmpty()) return stack;

        long budget = budgetSupplier.get();
        // 实际可接受量受预算限制
        long amt = Math.min(stack.getAmount(), budget);
        if (amt <= 0) return stack; // 预算耗尽，全部退回

        if (action.execute()) {
            budgetConsumer.accept(amt);
            // 化学品被虚空销毁，不存储
        }

        // 计算并返回未被接受的剩余量
        long remaining = stack.getAmount() - amt;
        if (remaining <= 0) return ChemicalStack.EMPTY;
        return stack.getChemical().getStack(remaining);
    }

    /** 提取化学品 —— 虚空槽始终返回空 */
    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        return ChemicalStack.EMPTY;
    }

    // ── 无 tank 参数的快捷方法 ──

    @Override
    public ChemicalStack insertChemical(ChemicalStack stack, Action action) {
        return insertChemical(0, stack, action);
    }

    @Override
    public ChemicalStack extractChemical(long amount, Action action) {
        return ChemicalStack.EMPTY;
    }
}
