package com.yelle233.yuanliuwujin.block;

import com.yelle233.yuanliuwujin.blockentity.DestructionMachineBlockEntity;
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
 * 销毁机器方块。
 */
public class DestructionMachineBlock extends Block implements EntityBlock {

    public static final BooleanProperty DIRTY = BooleanProperty.create("dirty");
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public static final VoxelShape SHAPE = Stream.of(
            Block.box(0, 14, 0, 2, 16, 2), Block.box(14, 0, 0, 16, 2, 2),
            Block.box(0, 0, 14, 2, 2, 16), Block.box(14, 0, 14, 16, 2, 16),
            Block.box(14, 14, 0, 16, 16, 2), Block.box(0, 14, 14, 2, 16, 16),
            Block.box(14, 14, 14, 16, 16, 16), Block.box(0, 0, 0, 2, 2, 2),
            Block.box(1, 1, 1, 15, 15, 15), Block.box(2, 15, 2, 14, 16, 14)
    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

    public DestructionMachineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(DIRTY, false).setValue(LIT, false));
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, BlockGetter level,
                                         BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRTY, LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DestructionMachineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return type == ModBlockEntities.DESTRUCTION_MACHINE.get()
                ? (lvl, pos, st, be) -> DestructionMachineBlockEntity.tick(lvl, pos, st,
                        (DestructionMachineBlockEntity) be)
                : null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DestructionMachineBlockEntity m) {
                ItemStack core = m.getCoreSlot().getStackInSlot(0);
                if (!core.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), core);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
