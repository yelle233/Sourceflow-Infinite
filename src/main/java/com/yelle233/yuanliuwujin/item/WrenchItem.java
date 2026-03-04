package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.blockentity.ICoreMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 扳手物品，用于操作机器核心和配置
 * <p>
 * 两种模式（Shift+滚轮切换）：
 * <ul>
 *   <li>IO 模式：右键插入/取出核心</li>
 *   <li>CONFIG 模式：右键切换面模式，潜行右键调整速率</li>
 * </ul>
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties props) { super(props); }

    // ── 扳手模式 ──

    public enum WrenchMode {
        IO, CONFIG;
        public WrenchMode next(int delta) {
            int i = (this.ordinal() + (delta > 0 ? 1 : -1) + values().length) % values().length;
            return values()[i];
        }
    }

    private static final String TAG_MODE = "WrenchMode";

    public static WrenchMode getMode(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return WrenchMode.IO;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG_MODE)) return WrenchMode.IO;
        try { return WrenchMode.valueOf(tag.getString(TAG_MODE)); }
        catch (IllegalArgumentException e) { return WrenchMode.IO; }
    }

    public static void setMode(ItemStack stack, WrenchMode mode) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> {
            CompoundTag tag = data.copyTag();
            tag.putString(TAG_MODE, mode.name());
            return CustomData.of(tag);
        });
    }

    // ── 右键交互 ──

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);

        // 非本模组机器：不拦截交互，让玩家正常操作其他方块（如打开箱子等）
        if (!(be instanceof ICoreMachine machine)) return InteractionResult.PASS;

        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        WrenchMode mode = getMode(ctx.getItemInHand());
        Direction face = ctx.getClickedFace();

        return (mode == WrenchMode.IO)
                ? handleIOMode(level, pos, player, machine)
                : handleConfigMode(level, pos, player, machine, face, player.isShiftKeyDown());
    }

    // ── IO 模式 ──

    private InteractionResult handleIOMode(Level level, BlockPos pos,
                                            Player player, ICoreMachine machine) {
        var coreSlot = machine.getCoreSlot();
        ItemStack offhand = player.getOffhandItem();

        // 潜行右键：拆除机器
        if (player.isShiftKeyDown()) {
            return dismantleMachine(level, pos, player, machine);
        }

        // 左手空着：取出核心
        if (offhand.isEmpty()) {
            ItemStack inSlot = coreSlot.getStackInSlot(0);
            if (inSlot.isEmpty()) return InteractionResult.FAIL;
            if (!player.getInventory().add(inSlot.copy())) {
                player.drop(inSlot.copy(), false);
            }
            coreSlot.setStackInSlot(0, ItemStack.EMPTY);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1f);
            return InteractionResult.SUCCESS;
        }

        // 左手有核心：插入核心
        if (!machine.isValidCoreItem(offhand.getItem())) return InteractionResult.PASS;
        if (!coreSlot.getStackInSlot(0).isEmpty()) return InteractionResult.FAIL;
        ItemStack toInsert = offhand.split(1);
        coreSlot.setStackInSlot(0, toInsert);
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1f, 1f);
        return InteractionResult.SUCCESS;
    }

    // ── 拆除机器 ──

    private InteractionResult dismantleMachine(Level level, BlockPos pos,
                                                Player player, ICoreMachine machine) {
        BlockState state = level.getBlockState(pos);

        // 取出核心并返还给玩家
        ItemStack core = machine.getCoreSlot().getStackInSlot(0);
        if (!core.isEmpty()) {
            if (!player.getInventory().add(core.copy())) {
                player.drop(core.copy(), false);
            }
            // 清空核心槽位，避免 onRemove 重复掉落
            machine.getCoreSlot().setStackInSlot(0, ItemStack.EMPTY);
        }

        // 掉落机器方块
        ItemStack blockItem = new ItemStack(state.getBlock());
        if (!player.getInventory().add(blockItem)) {
            player.drop(blockItem, false);
        }

        // 移除方块
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);

        return InteractionResult.SUCCESS;
    }

    // ── CONFIG 模式 ──

    private InteractionResult handleConfigMode(Level level, BlockPos pos,
                                                Player player, ICoreMachine machine,
                                                Direction face, boolean sneaking) {
        if (face == Direction.UP || face == Direction.DOWN) return InteractionResult.PASS;

        if (sneaking) {
            machine.adjustFaceRate(face, 10);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3f, 1.2f);
        } else {
            machine.cycleSideMode(face);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }
}
