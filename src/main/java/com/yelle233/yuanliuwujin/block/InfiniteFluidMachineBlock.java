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
            Block.box(2, 15, 2, 14, 16, 14),
            Block.box(14.001, 0.001, 14.001, 15.999, 1.999, 15.999),
            Block.box(14, 1, 1, 15, 15, 15),
            Block.box(1, 1, 1, 2, 15, 15),
            Block.box(0.999, 0.999, 0.999, 15.001, 15.001, 2.001),
            Block.box(0.999, 0.999, 13.999, 15.001, 15.001, 15.001),
            Block.box(9.5, 0, 6.5, 10, 1, 9.5),
            Block.box(6, 0, 6.5, 6.5, 1, 9.5),
            Block.box(6.5, 0, 6, 9.5, 1, 6.5),
            Block.box(6.5, 0, 9.5, 9.5, 1, 10),
            Block.box(10, 0.5, 5, 11, 2.5, 10),
            Block.box(6, 0.5, 5, 10, 2.5, 6),
            Block.box(6, 0.5, 6, 10, 2.5, 10),
            Block.box(5, 0.5, 5, 6, 2.5, 10),
            Block.box(5, 0.5, 10, 11, 2.5, 11),
            Block.box(11, 0.5, 11, 11, 2.5, 12),
            Block.box(6, 2, 6, 10, 3, 10),
            Block.box(4.5, 0.5, 3.5, 12.5, 1.5, 4.5),
            Block.box(11.5, 0.5, 4.5, 12.5, 1.5, 11.5),
            Block.box(3.5, 0.5, 3.5, 4.5, 1.5, 11.5),
            Block.box(3.5, 0.5, 11.5, 12.5, 1.5, 12.5),
            Block.box(11, 0, 4, 12, 2, 5),
            Block.box(5, 0, 4, 11, 2, 5),
            Block.box(11, 0, 11, 12, 2, 12),
            Block.box(4, 0, 5, 5, 2, 11),
            Block.box(4, 0, 4, 5, 2, 5),
            Block.box(4, 0, 11, 5, 2, 12),
            Block.box(11, 0, 5, 12, 2, 11),
            Block.box(5, 0, 11, 11, 2, 12),
            Block.box(14.001, 14.001, 0.001, 15.999, 15.999, 1.999),
            Block.box(6, 6.5, 0, 6.5, 9.5, 1),
            Block.box(6.5, 6, 0, 9.5, 6.5, 1),
            Block.box(6.5, 9.5, 0, 9.5, 10, 1),
            Block.box(9.5, 6.5, 0, 10, 9.5, 1),
            Block.box(5, 5, 0.5, 6, 10, 2.5),
            Block.box(6, 5, 0.5, 10, 6, 2.5),
            Block.box(6, 6, 0.5, 10, 10, 2.5),
            Block.box(10, 5, 0.5, 11, 10, 2.5),
            Block.box(5, 10, 0.5, 11, 11, 2.5),
            Block.box(5, 11, 0.5, 5, 12, 2.5),
            Block.box(6, 6, 2, 10, 10, 3),
            Block.box(3.5, 3.5, 0.5, 11.5, 4.5, 1.5),
            Block.box(3.5, 4.5, 0.5, 4.5, 11.5, 1.5),
            Block.box(11.5, 3.5, 0.5, 12.5, 11.5, 1.5),
            Block.box(4, 4, 0, 5, 5, 2),
            Block.box(5, 4, 0, 11, 5, 2),
            Block.box(4, 11, 0, 5, 12, 2),
            Block.box(11, 5, 0, 12, 11, 2),
            Block.box(11, 4, 0, 12, 5, 2),
            Block.box(11, 11, 0, 12, 12, 2),
            Block.box(4, 5, 0, 5, 11, 2),
            Block.box(5, 11, 0, 11, 12, 2),
            Block.box(3.5, 11.5, 0.5, 12.5, 12.5, 1.5),
            Block.box(0.001, 14.001, 14.001, 1.999, 15.999, 15.999),
            Block.box(0, 6.5, 9.5, 1, 9.5, 10),
            Block.box(0, 6, 6.5, 1, 6.5, 9.5),
            Block.box(0, 9.5, 6.5, 1, 10, 9.5),
            Block.box(0, 6.5, 6, 1, 9.5, 6.5),
            Block.box(0.5, 5, 10, 2.5, 10, 11),
            Block.box(0.5, 5, 6, 2.5, 6, 10),
            Block.box(0.5, 6, 6, 2.5, 10, 10),
            Block.box(0.5, 5, 5, 2.5, 10, 6),
            Block.box(0.5, 10, 5, 2.5, 11, 11),
            Block.box(0.5, 11, 11, 2.5, 12, 11),
            Block.box(1, 6, 6, 3, 10, 10),
            Block.box(0.5, 3.5, 4.5, 1.5, 4.5, 12.5),
            Block.box(0.5, 4.5, 11.5, 1.5, 11.5, 12.5),
            Block.box(0.5, 3.5, 3.5, 1.5, 11.5, 4.5),
            Block.box(0.5, 11.5, 3.5, 1.5, 12.5, 12.5),
            Block.box(0, 4, 11, 2, 5, 12),
            Block.box(0, 4, 5, 2, 5, 11),
            Block.box(0, 11, 11, 2, 12, 12),
            Block.box(0, 5, 4, 2, 11, 5),
            Block.box(0, 4, 4, 2, 5, 5),
            Block.box(0, 11, 4, 2, 12, 5),
            Block.box(0, 5, 11, 2, 11, 12),
            Block.box(0, 11, 5, 2, 12, 11),
            Block.box(15, 6.5, 6, 16, 9.5, 6.5),
            Block.box(15, 6, 6.5, 16, 6.5, 9.5),
            Block.box(15, 9.5, 6.5, 16, 10, 9.5),
            Block.box(15, 6.5, 9.5, 16, 9.5, 10),
            Block.box(13.5, 5, 5, 15.5, 10, 6),
            Block.box(13.5, 5, 6, 15.5, 6, 10),
            Block.box(13.5, 6, 6, 15.5, 10, 10),
            Block.box(13.5, 5, 10, 15.5, 10, 11),
            Block.box(13.5, 11, 5, 15.5, 12, 5),
            Block.box(13.5, 10, 5, 15.5, 11, 11),
            Block.box(13, 6, 6, 14, 10, 10),
            Block.box(14.5, 3.5, 3.5, 15.5, 4.5, 11.5),
            Block.box(14.5, 4.5, 3.5, 15.5, 11.5, 4.5),
            Block.box(14.5, 3.5, 11.5, 15.5, 11.5, 12.5),
            Block.box(14.5, 11.5, 3.5, 15.5, 12.5, 12.5),
            Block.box(14, 4, 4, 16, 5, 5),
            Block.box(14, 4, 5, 16, 5, 11),
            Block.box(14, 11, 4, 16, 12, 5),
            Block.box(14, 5, 11, 16, 11, 12),
            Block.box(14, 4, 11, 16, 5, 12),
            Block.box(14, 11, 11, 16, 12, 12),
            Block.box(14, 5, 4, 16, 11, 5),
            Block.box(14, 11, 5, 16, 12, 11),
            Block.box(9.5, 6.5, 15, 10, 9.5, 16),
            Block.box(6, 6.5, 15, 6.5, 9.5, 16),
            Block.box(6.5, 6, 15, 9.5, 6.5, 16),
            Block.box(6.5, 9.5, 15, 9.5, 10, 16),
            Block.box(10, 5, 13.5, 11, 10, 15.5),
            Block.box(6, 5, 13.5, 10, 6, 15.5),
            Block.box(6, 6, 13.5, 10, 10, 15.5),
            Block.box(5, 5, 13.5, 6, 10, 15.5),
            Block.box(5, 10, 13.5, 11, 11, 15.5),
            Block.box(11, 11, 13.5, 11, 12, 15.5),
            Block.box(6, 6, 13, 10, 10, 14),
            Block.box(4.5, 3.5, 14.5, 12.5, 4.5, 15.5),
            Block.box(11.5, 4.5, 14.5, 12.5, 11.5, 15.5),
            Block.box(3.5, 3.5, 14.5, 4.5, 11.5, 15.5),
            Block.box(11, 4, 14, 12, 5, 16),
            Block.box(5, 4, 14, 11, 5, 16),
            Block.box(11, 11, 14, 12, 12, 16),
            Block.box(4, 5, 14, 5, 11, 16),
            Block.box(4, 4, 14, 5, 5, 16),
            Block.box(4, 11, 14, 5, 12, 16),
            Block.box(11, 5, 14, 12, 11, 16),
            Block.box(5, 11, 14, 11, 12, 16),
            Block.box(3.5, 11.5, 14.5, 12.5, 12.5, 15.5),
            Block.box(14.001, 14.001, 14.001, 15.999, 15.999, 15.999),
            Block.box(0.001, 0.001, 14.001, 1.999, 1.999, 15.999),
            Block.box(0.001, 0.001, 0.001, 1.999, 1.999, 1.999),
            Block.box(0.001, 14.001, 0.001, 1.999, 15.999, 1.999),
            Block.box(14.001, 0.001, 0.001, 15.999, 1.999, 1.999)
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
