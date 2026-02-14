package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
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

/**
 * 扳手物品（1.20.1 Forge 版本）。
 * <p>
 * 模式存储改为直接使用 NBT Tag。
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
        if (!(level.getBlockEntity(pos) instanceof InfiniteFluidMachineBlockEntity machine)) {
            return InteractionResult.PASS;
        }

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        WrenchMode mode = getMode(ctx.getItemInHand());
        Direction face = ctx.getClickedFace();

        return (mode == WrenchMode.IO)
                ? handleIOMode(level, pos, player, machine)
                : handleConfigMode(level, pos, player, machine, face);
    }

    /* ====== IO 模式 ====== */

    private InteractionResult handleIOMode(Level level, BlockPos pos, Player player,
                                           InfiniteFluidMachineBlockEntity machine) {
        if (player.isShiftKeyDown()) {
            ItemStack core = machine.getCoreSlot().getStackInSlot(0);
            if (core.isEmpty()) return InteractionResult.PASS;

            machine.getCoreSlot().setStackInSlot(0, ItemStack.EMPTY);
            machine.setChanged();
            machine.onCoreChanged();
            player.addItem(core);

            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        ItemStack offhand = player.getOffhandItem();
        if (!(offhand.getItem() instanceof InfiniteCoreItem)) return InteractionResult.PASS;
        if (!machine.getCoreSlot().getStackInSlot(0).isEmpty()) return InteractionResult.PASS;

        ItemStack toInsert = offhand.copy();
        toInsert.setCount(1);
        machine.getCoreSlot().setStackInSlot(0, toInsert);
        machine.setChanged();
        machine.onCoreChanged();
        offhand.shrink(1);

        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /* ====== CONFIG 模式 ====== */

    private InteractionResult handleConfigMode(Level level, BlockPos pos, Player player,
                                               InfiniteFluidMachineBlockEntity machine, Direction face) {
        if (face == Direction.UP) return InteractionResult.PASS;

        machine.cycleSideMode(face);
        level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5f, 1.0f);
        return InteractionResult.SUCCESS;
    }
}
