package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

public class WrenchItem extends Item {
    public WrenchItem(Properties props) { super(props); }
    public enum WrenchMode {
        IO, CONFIG;

        public WrenchMode next(int delta) { // delta: +1/-1
            int i = (this.ordinal() + (delta > 0 ? 1 : -1) + values().length) % values().length;
            return values()[i];
        }
    }

    public static final String TAG_MODE = "WrenchMode";

    public static WrenchMode getMode(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return WrenchMode.IO;

        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG_MODE)) return WrenchMode.IO;

        String s = tag.getString(TAG_MODE);
        try {
            return WrenchMode.valueOf(s);
        } catch (IllegalArgumentException e) {
            return WrenchMode.IO;
        }
    }

    public static void setMode(ItemStack stack, WrenchMode mode) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> {
            CompoundTag tag = data.copyTag();
            tag.putString(TAG_MODE, mode.name());
            return CustomData.of(tag);
        });

    }


    @Override
    public InteractionResult useOn(UseOnContext ctx) {

        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS; // 客户端先成功，防止手感卡

        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) {
            return InteractionResult.PASS;
        }

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        WrenchMode mode = WrenchItem.getMode(ctx.getItemInHand());
        Direction face = ctx.getClickedFace();

        if (mode == WrenchMode.IO) {
            return handleIOMode(level, pos, player, machine);
        } else {
            return handleConfigMode(level, pos, player, machine, face);
        }
    }

    private InteractionResult handleIOMode(Level level, BlockPos pos, Player player, InfiniteFluidMachineBlockEntity machine) {
        boolean sneaking = player.isShiftKeyDown();

        if (sneaking) {
            // 潜行取出
            ItemStack core = machine.getCoreSlot().getStackInSlot(0);
            if (!core.isEmpty()) {
                machine.getCoreSlot().setStackInSlot(0, ItemStack.EMPTY);
                machine.setChanged();
                machine.onCoreChanged();

                player.addItem(core);

                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        // 非潜行：左手（副手）必须拿无限核心才能塞入
        ItemStack off = player.getOffhandItem();
        if (!(off.getItem() instanceof InfiniteCoreItem)) return InteractionResult.PASS;

        // 机器里必须空槽
        if (!machine.getCoreSlot().getStackInSlot(0).isEmpty()) return InteractionResult.PASS;

        ItemStack toInsert = off.copy();
        toInsert.setCount(1);

        machine.getCoreSlot().setStackInSlot(0, toInsert);
        machine.setChanged();
        machine.onCoreChanged();

        off.shrink(1);

        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleConfigMode(Level level, BlockPos pos, Player player,
                                               InfiniteFluidMachineBlockEntity machine, Direction face) {
        if (face == Direction.UP) return InteractionResult.PASS;

        machine.cycleSideMode(face);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, 1.2f);
        return InteractionResult.SUCCESS;
    }






}
