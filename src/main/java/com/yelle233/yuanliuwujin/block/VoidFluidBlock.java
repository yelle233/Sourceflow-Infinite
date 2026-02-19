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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚空流体方块。
 * <p>
 * 生命周期分为两个阶段：
 * <ol>
 *   <li><b>活跃阶段</b>（恩惠期）：快速扩散、积极销毁方块，不衰减浓度。
 *       持续时间由 {@code VOID_GRACE_PERIOD} 配置控制。</li>
 *   <li><b>衰减阶段</b>：停止扩散，浓度逐渐降低直到消失。</li>
 * </ol>
 */
public class VoidFluidBlock extends LiquidBlock {

    public static final IntegerProperty CONCENTRATION = IntegerProperty.create("concentration", 1, 15);

    /**
     * 记录每个虚空流体方块的"出生时间"（gameTime），用于判断恩惠期是否结束。
     * key = dimension hash XOR pos.asLong()，value = 出生时的 gameTime。
     * 方块被移除时清理条目。
     */
    private static final Map<Long, Long> BIRTH_TIMES = new ConcurrentHashMap<>();

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

    // ═══════════════════════════════════════════════════════════
    //  生命周期
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            // 记录出生时间
            long key = packKey(level, pos);
            if (!BIRTH_TIMES.containsKey(key)) {
                BIRTH_TIMES.put(key, level.getGameTime());
            }
            // 立即安排第一次活跃 tick（快速间隔）
            int spreadInterval = Modconfigs.VOID_SPREAD_INTERVAL.get();
            level.scheduleTick(pos, this, spreadInterval);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide) {
            // 仅当方块被真正移除（替换为非虚空流体方块）时才清理出生时间
            // 如果只是流体等级变化（还是 VoidFluidBlock），不清除——否则恩惠期会被不断重置
            if (!(newState.getBlock() instanceof VoidFluidBlock)) {
                BIRTH_TIMES.remove(packKey(level, pos));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  调度 Tick（主要逻辑）
    // ═══════════════════════════════════════════════════════════

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.is(this)) return;

        int concentration = state.getValue(CONCENTRATION);
        int fluidLevel = state.getValue(LEVEL);
        boolean isSource = state.getFluidState().isSource();

        // 判断是否还在恩惠期
        boolean inGracePeriod = isInGracePeriod(level, pos);

        if (inGracePeriod) {
            // ── 活跃阶段：快速扩散 + 销毁，不衰减 ──
            destroyAdjacentBlocks(state, level, pos, 32);

            // 积极扩散（源方块和高浓度流动块都可扩散，每次多个方向）
            if (concentration > 1) {
                trySpreadAggressive(state, level, pos, concentration, isSource);
            }

            // 安排下一次活跃 tick
            int spreadInterval = Modconfigs.VOID_SPREAD_INTERVAL.get();
            level.scheduleTick(pos, this, spreadInterval);

        } else {
            // ── 衰减阶段：浓度递减，不再扩散 ──
            int decay = Modconfigs.VOID_DECAY_PER_20T.get();
            if (!isSource) decay = Math.max(decay * 2, 2);

            int newConc = concentration - decay;
            if (newConc <= 0) {
                BIRTH_TIMES.remove(packKey(level, pos));
                level.removeBlock(pos, false);
            } else {
                level.setBlock(pos, state.setValue(CONCENTRATION, newConc), Block.UPDATE_ALL);
                int decayInterval = Modconfigs.VOID_DECAY_INTERVAL.get();
                if (!isSource) decayInterval = Math.max(decayInterval / 2, 5);
                level.scheduleTick(pos, this, decayInterval);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  randomTick（兜底 + 额外销毁）
    // ═══════════════════════════════════════════════════════════

    @Override
    public boolean isRandomlyTicking(BlockState state) { return true; }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 始终销毁相邻方块（randomTick 补充破坏力度）
        destroyAdjacentBlocks(state, level, pos, 32);

        // 如果没有挂起的 scheduledTick，重新安排
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            boolean inGrace = isInGracePeriod(level, pos);
            int interval = inGrace
                    ? Modconfigs.VOID_SPREAD_INTERVAL.get()
                    : Modconfigs.VOID_DECAY_INTERVAL.get();
            level.scheduleTick(pos, this, interval);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  恩惠期判断
    // ═══════════════════════════════════════════════════════════

    private boolean isInGracePeriod(ServerLevel level, BlockPos pos) {
        return isBlockInGracePeriod(level, pos);
    }

    /**
     * 公共方法：检查指定位置的虚空流体方块是否仍在恩惠期内。
     * 供 {@link com.yelle233.yuanliuwujin.fluid.VoidFluid} 调用以决定是否允许流体扩散。
     */
    public static boolean isBlockInGracePeriod(ServerLevel level, BlockPos pos) {
        long key = packKey(level, pos);
        Long birthTime = BIRTH_TIMES.get(key);
        if (birthTime == null) {
            // 没有出生记录（可能是区块重载），重新注册并给予恩惠期
            BIRTH_TIMES.put(key, level.getGameTime());
            return true;
        }
        long age = level.getGameTime() - birthTime;
        return age < Modconfigs.VOID_GRACE_PERIOD.get();
    }

    // ═══════════════════════════════════════════════════════════
    //  扩散与销毁
    // ═══════════════════════════════════════════════════════════

    /**
     * 积极扩散：同时向多个方向扩散（活跃阶段专用）。
     * 源方块可向所有方向扩散；非源方块只能向下。
     */
    private static void trySpreadAggressive(BlockState myState, ServerLevel level,
                                             BlockPos pos, int concentration, boolean isSource) {
        int maxRadius = Modconfigs.VOID_MAX_SPREAD_RADIUS.get();
        if (concentration <= (15 - maxRadius)) return;

        Direction[] dirs = isSource
                ? new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST,
                                  Direction.WEST, Direction.DOWN, Direction.UP}
                : new Direction[]{Direction.DOWN};

        int spreadCount = 0;
        int maxPerTick = isSource ? 3 : 1;  // 源方块每次最多扩散3个方向

        for (Direction dir : dirs) {
            if (spreadCount >= maxPerTick) break;
            BlockPos target = pos.relative(dir);
            BlockState targetState = level.getBlockState(target);

            if (targetState.isAir() || (!targetState.is(Blocks.BEDROCK)
                    && !(targetState.getBlock() instanceof VoidFluidBlock)
                    && targetState.getDestroySpeed(level, target) >= 0)) {
                // 清除目标位置的方块（如果不是空气）
                if (!targetState.isAir()) {
                    level.removeBlock(target, false);
                }

                int newConc = Math.max(1, concentration - 1);
                BlockState newState = ModBlocks.VOID_FLUID_BLOCK.get().defaultBlockState()
                        .setValue(LEVEL, 6)
                        .setValue(CONCENTRATION, newConc);
                level.setBlock(target, newState, Block.UPDATE_ALL);
                spreadCount++;
            }
        }
    }

    private static void destroyAdjacentBlocks(BlockState myState, ServerLevel level,
                                               BlockPos pos, int maxCount) {
        int destroyed = 0;
        for (Direction dir : Direction.values()) {
            if (destroyed >= maxCount) break;
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.isAir()) continue;
            if (neighborState.is(Blocks.BEDROCK)) continue;
            if (neighborState.getBlock() instanceof VoidFluidBlock) continue;
            level.removeBlock(neighbor, false);
            destroyed++;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  实体接触伤害
    // ═══════════════════════════════════════════════════════════

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;
        if (entity instanceof ItemEntity) { entity.discard(); return; }
        DamageSource voidDamage = level.damageSources().fellOutOfWorld();
        entity.hurt(voidDamage, Float.MAX_VALUE);
        if (entity.isAlive()) entity.discard();
    }

    // ═══════════════════════════════════════════════════════════
    //  公共工具方法
    // ═══════════════════════════════════════════════════════════

    /** 在指定位置放置虚空流体源方块 */
    public static void placeAt(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (current.is(Blocks.BEDROCK)) return;
        if (current.getBlock() instanceof VoidFluidBlock) return;
        // 先清除非空气方块
        if (!current.isAir()) {
            level.removeBlock(pos, false);
        }
        BlockState state = ModBlocks.VOID_FLUID_BLOCK.get().defaultBlockState()
                .setValue(LEVEL, 8).setValue(CONCENTRATION, 15);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        // onPlace 会自动记录出生时间并调度 scheduledTick
    }

    /** 清理所有缓存的出生时间（服务器关闭时调用） */
    public static void clearBirthTimes() {
        BIRTH_TIMES.clear();
    }

    /** 维度+坐标 → 唯一 key */
    private static long packKey(Level level, BlockPos pos) {
        return pos.asLong() ^ ((long) level.dimension().location().hashCode() * 0x9E3779B97F4A7C15L);
    }
}
