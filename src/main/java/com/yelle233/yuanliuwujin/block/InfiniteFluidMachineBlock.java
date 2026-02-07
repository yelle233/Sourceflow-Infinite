package com.yelle233.yuanliuwujin.block;

import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class InfiniteFluidMachineBlock extends Block implements EntityBlock {
    public InfiniteFluidMachineBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfiniteFluidMachineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return type == ModBlockEntities.INFINITE_FLUID_MACHINE.get()
                ? (lvl, pos, st, be) -> InfiniteFluidMachineBlockEntity.serverTick(lvl, pos, st, (InfiniteFluidMachineBlockEntity) be)
                : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return InteractionResult.PASS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) {
            return InteractionResult.PASS;
        }

        ItemStack inSlot = machine.getCoreSlot().getStackInSlot(0);
        if (inSlot.isEmpty()) {
            return InteractionResult.CONSUME;
        }

        // 清空机器槽位
        machine.getCoreSlot().setStackInSlot(0, ItemStack.EMPTY);
        machine.setChanged();

        // 给玩家（背包满就丢地上）
        if (!player.addItem(inSlot)) {
            player.drop(inSlot, false);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        // 只处理“无限核心”
        if (!(stack.getItem() instanceof InfiniteCoreItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 槽里已经有核心了就不塞
        if (!machine.getCoreSlot().getStackInSlot(0).isEmpty()) {
            return ItemInteractionResult.CONSUME;
        }

        // 放入 1 个
        ItemStack toInsert = stack.copy();
        toInsert.setCount(1);
        machine.getCoreSlot().setStackInSlot(0, toInsert);
        machine.setChanged();

        // 扣掉玩家手上的 1 个
        stack.shrink(1);

        return ItemInteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // 只有在“方块真的被换掉/破坏”时才执行（避免同种方块状态更新时重复掉落）
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof InfiniteFluidMachineBlockEntity machine) {

                // 把核心槽里的物品丢出来
                ItemStack core = machine.getCoreSlot().getStackInSlot(0);
                if (!core.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, core);
                    machine.getCoreSlot().setStackInSlot(0, ItemStack.EMPTY);
                }
            }
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }


}
