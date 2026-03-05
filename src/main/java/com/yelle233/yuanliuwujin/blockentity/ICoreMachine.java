package com.yelle233.yuanliuwujin.blockentity;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 面速率单位为 mB/s（每秒）。
 */
public interface ICoreMachine {

    ItemStackHandler getCoreSlot();
    void cycleSideMode(Direction dir);
    void onCoreChanged();
    boolean isValidCoreItem(Item item);

    /** 获取指定侧面的流量速率（mB/s） */
    int getFaceRate(Direction dir);

    /** 调整指定侧面的流量速率（mB/s） */
    void adjustFaceRate(Direction dir, int delta);

    /** 设置最后交互的玩家（用于成就触发） */
    default void setLastInteractingPlayer(Player player) {}
}
