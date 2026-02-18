package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.blockentity.ICoreMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 扳手物品（1.20.1 Forge 版本）。
 * <p>
 * 通过 {@link ICoreMachine} 接口统一处理无限流体机器和销毁机器，无需重复逻辑。
 * 模式存储使用直接 NBT Tag（1.20.1 不支持 DataComponents）。
 * <p>
 * 两种模式（潜行+滚轮切换）：
 * <ul>
 *   <li><b>IO 模式</b>：非潜行右键从副手插入对应核心，潜行右键取出核心</li>
 *   <li><b>CONFIG 模式</b>：右键点击面循环切换该面的模式</li>
 * </ul>
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties props) {
        super(props);
    }

    /* ====== 扳手模式枚举 ====== */

    public enum WrenchMode {
        IO, CONFIG;

        public WrenchMode next(int delta) {
            int i = (this.ordinal() + (delta > 0 ? 1 : -1) + values().length) % values().length;
            return values()[i];
        }
    }

    private static final String TAG_MODE = "WrenchMode";

    /** 从 NBT 读取当前模式，默认 IO */
    public static WrenchMode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MODE)) return WrenchMode.IO;
        try {
            return WrenchMode.valueOf(tag.getString(TAG_MODE));
        } catch (IllegalArgumentException e) {
            return WrenchMode.IO;
        }
    }

    /** 将模式写入 NBT */
    public static void setMode(ItemStack stack, WrenchMode mode) {
        stack.getOrCreateTag().putString(TAG_MODE, mode.name());
    }

    /* ====== 右键交互 ====== */

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);

        // ── 通过 ICoreMachine 接口统一处理两种机器 ──
        if (!(be instanceof ICoreMachine machine)) {
            return InteractionResult.PASS;
        }

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        WrenchMode mode = getMode(ctx.getItemInHand());
        Direction face  = ctx.getClickedFace();

        return (mode == WrenchMode.IO)
                ? handleIOMode(level, pos, player, machine)
                : handleConfigMode(level, pos, player, machine, face);
    }

    /* ====== IO 模式：插入/取出核心 ====== */

    /**
     * 处理 IO 模式交互。
     * <p>
     * 利用 {@link ICoreMachine#isValidCoreItem(Item)} 判断副手物品是否为
     * 当前机器对应的核心类型，实现两种机器的差异化核心插入限制。
     */
    private InteractionResult handleIOMode(Level level, BlockPos pos, Player player,
                                            ICoreMachine machine) {
        if (player.isShiftKeyDown()) {
            // 潜行：取出核心
            ItemStack core = machine.getCoreSlot().getStackInSlot(0);
            if (core.isEmpty()) return InteractionResult.PASS;

            machine.getCoreSlot().setStackInSlot(0, ItemStack.EMPTY);
            machine.onCoreChanged();
            player.addItem(core);

            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        // 非潜行：从副手插入核心
        ItemStack offhand = player.getOffhandItem();

        // isValidCoreItem() 由各机器自行实现，确保类型匹配：
        // 无限流体机器 → InfiniteCoreItem；销毁机器 → DestructionCoreItem
        if (!machine.isValidCoreItem(offhand.getItem())) return InteractionResult.PASS;
        if (!machine.getCoreSlot().getStackInSlot(0).isEmpty()) return InteractionResult.PASS;

        ItemStack toInsert = offhand.copy();
        toInsert.setCount(1);
        machine.getCoreSlot().setStackInSlot(0, toInsert);
        machine.onCoreChanged();
        offhand.shrink(1);

        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /* ====== CONFIG 模式：切换面模式 ====== */

    private InteractionResult handleConfigMode(Level level, BlockPos pos, Player player,
                                               ICoreMachine machine, Direction face) {
        // 顶面不可配置（用于接收能量）
        if (face == Direction.UP) return InteractionResult.PASS;

        machine.cycleSideMode(face);
        level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.5f, 1.0f);
        return InteractionResult.SUCCESS;
    }
}
