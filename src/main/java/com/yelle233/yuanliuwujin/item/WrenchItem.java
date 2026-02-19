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

/**
 * 扳手物品，用于操作无限流体机器和销毁机器。
 * <p>
 * <b>两种模式（Shift+滚轮切换）：</b>
 * <ul>
 *   <li><b>IO 模式</b>：非潜行右键 → 从副手插入核心；潜行右键 → 取出核心</li>
 *   <li><b>CONFIG 模式</b>：右键侧面 → 循环切换面模式（OFF/PUSH/BOTH 或 OFF/PULL/BOTH）</li>
 *   <li>CONFIG 模式下，潜行右键侧面 → 速率 +10（单击）或持续增加（长按）</li>
 *   <li>CONFIG 模式下，Shift+滚轮上 → 速率 +1000；Shift+滚轮下 → 速率 -1000</li>
 * </ul>
 * <p>
 * 长按逻辑在客户端通过 {@link com.yelle233.yuanliuwujin.SourceflowInfiniteClient} 处理，
 * 此处 {@code useOn} 只处理单次点击。
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties props) { super(props); }

    // ── 扳手模式枚举 ──────────────────────────────────────────

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

    // ── 右键交互 ──────────────────────────────────────────────

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ICoreMachine machine)) return InteractionResult.PASS;

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        WrenchMode mode = getMode(ctx.getItemInHand());
        Direction face = ctx.getClickedFace();

        return (mode == WrenchMode.IO)
                ? handleIOMode(level, pos, player, machine)
                : handleConfigMode(level, pos, player, machine, face, player.isShiftKeyDown());
    }

    // ── IO 模式 ────────────────────────────────────────────────

    private InteractionResult handleIOMode(Level level, BlockPos pos,
                                            Player player, ICoreMachine machine) {
        var coreSlot = machine.getCoreSlot();

        if (player.isShiftKeyDown()) {
            // 取出核心
            ItemStack inSlot = coreSlot.getStackInSlot(0);
            if (inSlot.isEmpty()) return InteractionResult.FAIL;
            if (!player.getInventory().add(inSlot.copy())) {
                player.drop(inSlot.copy(), false);
            }
            coreSlot.setStackInSlot(0, ItemStack.EMPTY);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1f);
            return InteractionResult.SUCCESS;
        } else {
            // 从副手插入核心
            ItemStack offhand = player.getOffhandItem();
            if (offhand.isEmpty()) return InteractionResult.PASS;
            if (!machine.isValidCoreItem(offhand.getItem())) return InteractionResult.PASS;
            if (!coreSlot.getStackInSlot(0).isEmpty()) return InteractionResult.FAIL;
            ItemStack toInsert = offhand.split(1);
            coreSlot.setStackInSlot(0, toInsert);
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1f, 1f);
            return InteractionResult.SUCCESS;
        }
    }

    // ── CONFIG 模式 ────────────────────────────────────────────

    private InteractionResult handleConfigMode(Level level, BlockPos pos,
                                                Player player, ICoreMachine machine,
                                                Direction face, boolean sneaking) {
        // 顶面、底面不参与 CONFIG 操作
        if (face == Direction.UP || face == Direction.DOWN) return InteractionResult.PASS;

        if (sneaking) {
            // 潜行右键：速率 +10（单次点击；长按由客户端持续发包）
            machine.adjustFaceRate(face, 10);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3f, 1.2f);
        } else {
            // 非潜行右键：循环切换面模式
            machine.cycleSideMode(face);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }
}
