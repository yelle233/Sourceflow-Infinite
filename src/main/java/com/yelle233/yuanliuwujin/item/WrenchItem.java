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
 * 扳手物品（1.20.1 Forge v2.0 版本）。
 * <p>
 * <b>两种模式（Shift+滚轮切换）：</b>
 * <ul>
 *   <li><b>IO 模式</b>：非潜行右键从副手插入核心；潜行右键取出核心</li>
 *   <li><b>CONFIG 模式</b>：右键侧面循环切换面模式（OFF/PULL/BOTH 或 OFF/PUSH/BOTH）</li>
 *   <li>CONFIG 模式下，潜行右键侧面 → 速率 +10（单击）或持续增加（长按，客户端处理）</li>
 *   <li>CONFIG 模式下，Shift+滚轮上/下 → 速率 ±1000（通过网络包处理）</li>
 * </ul>
 * <p>
 * 面速率的实际调整由 {@link com.yelle233.yuanliuwujin.network.FaceRateUpdateMessage} 处理，
 * 客户端长按逻辑由 {@link com.yelle233.yuanliuwujin.SourceflowInfiniteClient} 处理。
 * 此处 {@code useOn} 只处理单次点击。
 * <p>
 * 模式存储使用 NBT Tag（1.20.1 Forge 不支持 DataComponents）。
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties props) { super(props); }

    // ── 模式枚举 ──────────────────────────────────────────

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
        try { return WrenchMode.valueOf(tag.getString(TAG_MODE)); }
        catch (IllegalArgumentException e) { return WrenchMode.IO; }
    }

    /** 将模式写入 NBT */
    public static void setMode(ItemStack stack, WrenchMode mode) {
        stack.getOrCreateTag().putString(TAG_MODE, mode.name());
    }

    // ── 右键交互 ──────────────────────────────────────────

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (!(be instanceof ICoreMachine machine)) return InteractionResult.PASS;

        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        WrenchMode mode = getMode(ctx.getItemInHand());
        Direction face  = ctx.getClickedFace();

        return (mode == WrenchMode.IO)
                ? handleIOMode(level, pos, player, machine)
                : handleConfigMode(level, pos, player, machine, face);
    }

    // ── IO 模式：插入/取出核心 ────────────────────────────

    private InteractionResult handleIOMode(Level level, BlockPos pos, Player player, ICoreMachine machine) {
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

    // ── CONFIG 模式：切换面模式 / 调整速率 ────────────────

    private InteractionResult handleConfigMode(Level level, BlockPos pos, Player player,
                                               ICoreMachine machine, Direction face) {
        if (face == Direction.UP) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            // 潜行右键：速率 +10（单击）
            machine.adjustFaceRate(face, 10);
            level.playSound(null, pos,
                    SoundEvents.UI_BUTTON_CLICK.value(),
                    SoundSource.PLAYERS, 0.4f, 1.2f);

        } else {
            // 非潜行右键：循环面模式
            machine.cycleSideMode(face);
            level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.5f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }
}

