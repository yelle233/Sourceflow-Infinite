package com.yelle233.yuanliuwujin.blockentity;

import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 拥有可插拔核心槽和面模式配置的机器公共接口。
 * <p>
 * 扳手通过此接口统一操作两种机器，无需关心具体实现。
 * 新增面速率（faceRate）相关方法，支持扳手在 CONFIG 模式下调整每面的流量速率。
 */
public interface ICoreMachine {

    /** 获取核心物品槽 Handler */
    ItemStackHandler getCoreSlot();

    /**
     * 循环切换指定面的输出/输入模式。
     * 顶面 ({@link Direction#UP}) 和底面 ({@link Direction#DOWN}) 实现时应忽略。
     */
    void cycleSideMode(Direction dir);

    /**
     * 当核心槽内容发生变化时通知机器（负责同步客户端、刷新 capability 等）。
     */
    void onCoreChanged();

    /**
     * 判断指定物品是否可作为该机器的核心插入。
     */
    boolean isValidCoreItem(Item item);

    /**
     * 获取指定侧面的流量速率（mB/tick）。
     * <p>
     * 仅对 4 个侧面（NORTH/SOUTH/EAST/WEST）有意义；
     * UP/DOWN 面速率固定，调用此方法无意义。
     *
     * @param dir 方向
     * @return 该面的速率（mB/tick），最小值为 1
     */
    int getFaceRate(Direction dir);

    /**
     * 调整指定侧面的流量速率。
     * <p>
     * 速率范围：[1, Integer.MAX_VALUE - 1]，溢出时 clamp 到边界值。
     *
     * @param dir   方向（UP/DOWN 无效）
     * @param delta 变化量（正数增加，负数减少），例如 +10、-1000
     */
    void adjustFaceRate(Direction dir, int delta);
}
