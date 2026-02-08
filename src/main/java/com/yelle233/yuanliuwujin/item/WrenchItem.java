package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class WrenchItem extends Item {
    public WrenchItem(Properties props) { super(props); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) return InteractionResult.PASS;

        if (face == Direction.UP) return InteractionResult.PASS;

        if (!level.isClientSide) {
            machine.cycleSideMode(face);
            // 可选：发 actionbar 提示
            player.displayClientMessage(Component.literal("Side " + face + " -> " + machine.getSideMode(face)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
