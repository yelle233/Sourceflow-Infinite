package com.yelle233.yuanliuwujin.block;

import com.yelle233.yuanliuwujin.blockentity.DestructionMachineBlockEntity;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
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
 * 销毁机器方块（1.20.1 Forge 版本）。
 * <p>
 * <ul>
 *   <li>四个面作为流体/化学品输入端（PUSH / BOTH 模式）</li>
 *   <li>顶面仅用于接收 FE 能量</li>
 *   <li>插入销毁核心且通电后，可销毁任意推入/吸入的流体或化学品</li>
 * </ul>
 */
public class DestructionMachineBlock extends Block implements EntityBlock {

    /** 脏标记：翻转此属性可强制使周围方块的 capability 缓存失效 */
    public static final BooleanProperty DIRTY = BooleanProperty.create("dirty");

    /** LIT 属性：插入销毁核心时发光（复用原版 LIT） */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public static final VoxelShape SHAPE = Stream.of(
            Block.box(0, 1, 0, 16, 15, 16),
            Block.box(1, 0, 1, 15, 16, 15)
    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

    public DestructionMachineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(DIRTY, false)
                .setValue(LIT, false));
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, BlockGetter level,
                                        BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRTY, LIT);
    }

    /* ====== EntityBlock 实现 ====== */

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DestructionMachineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        // 仅服务端 tick
        if (level.isClientSide) return null;
        if (type != ModBlockEntities.DESTRUCTION_MACHINE.get()) return null;
        return (lvl, pos, st, be) ->
                DestructionMachineBlockEntity.tick(lvl, pos, st,
                        (DestructionMachineBlockEntity) be);
    }

    /* ====== 方块被破坏 / 爆炸时掉落核心 ====== */

    private static void dropCore(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DestructionMachineBlockEntity machine)) return;

        ItemStack core = machine.getCoreSlot().getStackInSlot(0);
        if (core.isEmpty()) return;

        Containers.dropItemStack(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                core.copy());
        machine.getCoreSlot().setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        dropCore(level, pos);
        super.playerWillDestroy(level, pos, state, player);
    }


    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos,
                                net.minecraft.world.level.Explosion explosion) {
        dropCore(level, pos);
        super.onBlockExploded(state, level, pos, explosion);
    }
}
