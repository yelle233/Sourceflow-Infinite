package com.yelle233.yuanliuwujin.block;

import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.stream.Stream;

/**
 * 无限流体机器方块。
 * <p>
 * 实现 {@link EntityBlock} 以关联 {@link InfiniteFluidMachineBlockEntity}。
 * 包含一个 {@link #DIRTY} 布尔属性，用于在切换面模式时翻转状态以触发
 * 周围方块的 capability 重新查询。
 */
public class InfiniteFluidMachineBlock extends Block implements EntityBlock {


    /** 脏标记：翻转此属性可强制使周围方块的 capability 缓存失效 */
    public static final BooleanProperty DIRTY = BooleanProperty.create("dirty");


    // 定义一个 LIT 属性 (直接复用原版的 LIT 属性),用于让机器发光
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public InfiniteFluidMachineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(DIRTY, false));

        // 设置发光状态为(false为不发光)
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }


    //机器的碰撞箱体积
    public static final VoxelShape SHAPE = Stream.of(
            Block.box(0, 14, 0, 2, 16, 2),
            Block.box(14, 0, 0, 16, 2, 2),
            Block.box(0, 0, 14, 2, 2, 16),
            Block.box(14, 0, 14, 16, 2, 16),
            Block.box(14, 14, 0, 16, 16, 2),
            Block.box(0, 14, 14, 2, 16, 16),
            Block.box(14, 14, 14, 16, 16, 16),
            Block.box(0, 0, 0, 2, 2, 2),
            Block.box(1, 1, 1, 15, 15, 15),
            Block.box(2, 15, 2, 14, 16, 14)
    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

    @Override
    public @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }



    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRTY);
        builder.add(LIT);
    }

    /* ====== EntityBlock 实现 ====== */

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfiniteFluidMachineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 仅服务端 tick
        if (level.isClientSide) return null;
        if (type != ModBlockEntities.INFINITE_FLUID_MACHINE.get()) return null;
        return (lvl, pos, st, be) ->
                InfiniteFluidMachineBlockEntity.serverTick(lvl, pos, st, (InfiniteFluidMachineBlockEntity) be);
    }

    /* ====== 方块被破坏时掉落核心 ====== */

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof InfiniteFluidMachineBlockEntity machine) {
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
