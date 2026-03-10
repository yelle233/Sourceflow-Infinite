package com.yelle233.yuanliuwujin.block;

import com.yelle233.yuanliuwujin.blockentity.VoidGeneratorBlockEntity;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
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
 * 虚空发电机方块
 */
public class VoidGeneratorBlock extends Block implements EntityBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    // 完整方块形状（用于视觉轮廓和碰撞）
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public VoidGeneratorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, BlockGetter level,
                                         BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                                  BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return type == ModBlockEntities.VOID_GENERATOR.get()
                ? (lvl, pos, st, be) -> VoidGeneratorBlockEntity.tick(lvl, pos, st,
                        (VoidGeneratorBlockEntity) be)
                : null;
    }

    /**
     * 监听红石信号变化，切换所有输出面的开关状态
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof VoidGeneratorBlockEntity generator)) return;

        // 检测红石信号变化
        boolean hasSignal = level.hasNeighborSignal(pos);
        boolean wasSignal = generator.hadRedstoneSignal();

        // 红石信号从无到有：切换所有输出面的开关状态
        if (hasSignal && !wasSignal) {
            generator.toggleRedstoneControl();
        }

        // 更新红石信号状态
        generator.setRedstoneSignal(hasSignal);
    }
}
