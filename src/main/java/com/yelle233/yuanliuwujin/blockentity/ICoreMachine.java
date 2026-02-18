package com.yelle233.yuanliuwujin.blockentity;

import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 拥有可插拔核心槽和面模式配置的机器公共接口。
 * <p>
 * {@link com.yelle233.yuanliuwujin.item.WrenchItem} 通过此接口操作不同机器，
 * 无需关心具体实现，实现了无限流体机器与销毁机器的统一扳手交互。
 */
public interface ICoreMachine {

    /** 获取核心物品槽 Handler */
    ItemStackHandler getCoreSlot();

    /**
     * 循环切换指定面的输出/输入模式。
     * 顶面 ({@link Direction#UP}) 实现时应忽略。
     */
    void cycleSideMode(Direction dir);

    /**
     * 当核心槽内容发生变化时通知机器（负责同步客户端、刷新 capability 等）。
     * 实现中须调用 setChanged() 及必要的客户端同步。
     */
    void onCoreChanged();

    /**
     * 判断指定物品是否可作为该机器的核心插入。
     * 例如无限流体机器只接受 InfiniteCoreItem，销毁机器只接受 DestructionCoreItem。
     */
    boolean isValidCoreItem(Item item);
}
