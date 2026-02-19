package com.yelle233.yuanliuwujin.block;

import com.yelle233.yuanliuwujin.registry.ModBlocks;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * 虚空流体方块（Void Fluid Block）。
 * <p>
 * 此方块是虚空流体在世界中的物理表现形式，仅在机器爆炸时自动生成。
 * 特性：
 * <ol>
 *   <li><b>浓度衰减</b>：具有 CONCENTRATION 属性（1–15），每隔若干 tick 减 1，归零后消失</li>
 *   <li><b>方块销毁</b>：每次 scheduledTick 时，尝试删除相邻（6面）非基岩方块</li>
 *   <li><b>实体伤害</b>：实体进入时立即死亡（entityInside 触发），掉落物也被删除</li>
 *   <li><b>有限扩散</b>：源方块（浓度最高）会向相邻空气格扩散，最大扩散半径由配置控制</li>
 * </ol>
 * <p>
 * <b>性能注意</b>：方块销毁每次最多删除 8 个相邻方块，防止大量虚空方块同时触发卡顿。
 */
public class VoidFluidBlock extends LiquidBlock {

    /**
     * 浓度属性（1–15）。浓度越高，方块持续时间越长。
     * 初始浓度：15（源方块），扩散时浓度 = 源浓度 - 1（最小 1）。
     */
    public static final IntegerProperty CONCENTRATION = IntegerProperty.create("concentration", 1, 15);

    /** 最大扩散距离（从源头算，半径内的空气格才能被感染） */
    private static final int MAX_SPREAD_RADIUS = 4;

    public VoidFluidBlock(net.minecraft.world.level.material.FlowingFluid fluid,
                          BlockBehaviour.Properties properties) {
        super(fluid, properties);

        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LEVEL, 8)
                .setValue(CONCENTRATION, 15));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONCENTRATION);
    }

    // ── Tick 系统 ──────────────────────────────────────────────────

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true; // 启用 randomTick
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 1) 销毁相邻方块（每次 randomTick 最多销毁 8 个）
        destroyAdjacentBlocks(state, level, pos, 8);

        // 2) 尝试向相邻空气格扩散（仅源方块，即 LEVEL=8 或 concentration > 1）
        int concentration = state.getValue(CONCENTRATION);
        if (concentration > 1) {
            trySpread(state, level, pos, concentration);
        }

        // 3) 衰减浓度
        int decay = Modconfigs.VOID_DECAY_PER_20T.get();
        int newConc = concentration - decay;
        if (newConc <= 0) {
            level.removeBlock(pos, false); // 浓度耗尽，方块消失
        } else {
            level.setBlock(pos, state.setValue(CONCENTRATION, newConc), Block.UPDATE_ALL);
        }
    }

    /**
     * 销毁与此方块相邻（6面）的方块，跳过基岩与其他虚空流体方块。
     *
     * @param maxCount 本次最多销毁的方块数量（防止卡顿）
     */
    private static void destroyAdjacentBlocks(BlockState myState, ServerLevel level,
                                               BlockPos pos, int maxCount) {
        int destroyed = 0;
        for (Direction dir : Direction.values()) {
            if (destroyed >= maxCount) break;
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);
            // 跳过：基岩、空气、虚空流体本身
            if (neighborState.isAir()) continue;
            if (neighborState.is(Blocks.BEDROCK)) continue;
            if (neighborState.getBlock() instanceof VoidFluidBlock) continue;
            // 删除方块（不掉落物品）
            level.removeBlock(neighbor, false);
            destroyed++;
        }
    }

    /**
     * 向相邻空气格扩散虚空流体，扩散后浓度 - 1。
     * 只有当 concentration > 1 时才能继续扩散（防止浓度为1的块继续扩散）。
     */
    private static void trySpread(BlockState myState, ServerLevel level,
                                   BlockPos pos, int concentration) {
        int maxRadius = Modconfigs.VOID_MAX_SPREAD_RADIUS.get();
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST, Direction.DOWN}) {
            BlockPos target = pos.relative(dir);
            // 超出最大扩散半径则跳过（使用块实体追踪较复杂，这里用简单的距离估算：
            // 浓度越低，传播代次越多，当浓度从15降到 15-maxRadius 时停止扩散）
            if (concentration <= (15 - maxRadius)) continue;

            BlockState targetState = level.getBlockState(target);
            if (!targetState.isAir()) continue;
            // 在目标位置放置虚空流体方块，浓度 = 当前 - 1
            BlockState newState = ModBlocks.VOID_FLUID_BLOCK.get().defaultBlockState()
                    .setValue(LEVEL, 6) // 流动态
                    .setValue(CONCENTRATION, Math.max(1, concentration - 1));
            level.setBlock(target, newState, Block.UPDATE_ALL);
            break; // 每 tick 只扩散一格，防止指数爆炸
        }
    }

    // ── 实体交互 ──────────────────────────────────────────────────

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;
        // 掉落物直接删除
        if (entity instanceof ItemEntity) {
            entity.discard();
            return;
        }
        // 其他实体：立即造成极高伤害（虚空伤害无法被抵抗）
        DamageSource voidDamage = level.damageSources().fellOutOfWorld();
        entity.hurt(voidDamage, Float.MAX_VALUE);
        if (entity.isAlive()) {
            entity.discard(); // 若未死亡则直接删除
        }
    }

    // ── 工具方法：在指定位置放置虚空流体方块（爆炸时调用） ──────────

    /**
     * 在指定位置放置一个满浓度（15）的虚空流体源方块。
     * 若该位置不是空气，则不放置。
     */
    public static void placeAt(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return;
        BlockState state = ModBlocks.VOID_FLUID_BLOCK.get().defaultBlockState()
                .setValue(LEVEL, 8)
                .setValue(CONCENTRATION, 15);
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }
}
