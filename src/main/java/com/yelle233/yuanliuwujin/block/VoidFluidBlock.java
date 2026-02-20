package com.yelle233.yuanliuwujin.block;

import com.yelle233.yuanliuwujin.registry.ModBlocks;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
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
 * 虚空流体方块 —— 所有行为的唯一控制中心。
 * <p>
 * 设计原则：<b>完全脱离原版流体力学</b>。{@link com.yelle233.yuanliuwujin.fluid.VoidFluid}
 * 的 {@code tick()} 和 {@code spread()} 均为 no-op；扩散、销毁、衰减全部由本类的
 * {@link #tick(BlockState, ServerLevel, BlockPos, RandomSource)} 独立管理。
 * 这样彻底避免了原版 {@code getNewLiquid()} 重新计算流体状态导致衰减失效的问题。
 * <p>
 * 生命周期分为两个阶段：
 * <ol>
 *   <li><b>恩惠期（Grace Period）</b>：毁灭性扩散 + 销毁方块/实体，不衰减浓度。
 *       源方块向 6 个方向扩散，非源方块向下扩散。销毁范围含相邻方块。</li>
 *   <li><b>衰减阶段</b>：停止扩散和销毁，浓度逐步降低直到方块消失。
 *       源方块衰减较慢，非源方块衰减较快。</li>
 * </ol>
 */
public class VoidFluidBlock extends LiquidBlock {

    /** 浓度属性（1-15），决定流体的"寿命"。恩惠期后逐步衰减至 0 时方块消失。 */
    public static final IntegerProperty CONCENTRATION = IntegerProperty.create("concentration", 1, 15);


    /** 自定义伤害类型：虚空流体伤害 */
    public static final ResourceKey<DamageType> VOID_FLUID_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("yuanliuwujin", "void_fluid"));

    /**
     * 记录每个虚空流体方块的"出生时间"（gameTime），用于判断恩惠期是否结束。
     * key = dimension hash XOR pos.asLong()，value = 出生时的 gameTime。
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

    // ═══════════════════════════════════════════════════════════════
    //  生命周期回调
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                        BlockState oldState, boolean movedByPiston) {
        // 注意：不调用 super.onPlace() 以避免 LiquidBlock 调度原版流体 tick
        if (!level.isClientSide) {
            long key = packKey(level, pos);
            if (!BIRTH_TIMES.containsKey(key)) {
                BIRTH_TIMES.put(key, level.getGameTime());
            }
            // 调度第一次自定义 tick
            scheduleNextTick(level, pos, true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide) {
            // 仅在方块被真正替换为非虚空流体方块时才清理出生记录
            if (!(newState.getBlock() instanceof VoidFluidBlock)) {
                BIRTH_TIMES.remove(packKey(level, pos));
            }
        }
    }

    /**
     * 拦截原版 neighborChanged → 不让 LiquidBlock 调度原版流体 tick。
     * 改为：恩惠期内如果邻居变化（可能有新空间可扩散），调度自定义 tick。
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        // 不调用 super.neighborChanged()，避免 LiquidBlock 调度原版流体 tick
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            // 恩惠期内邻居变化 → 快速重新调度一次（可能有新位置可扩散）
            if (isInGracePeriod(sl, pos)) {
                if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
                    level.scheduleTick(pos, this, 2); // 快速响应
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  核心调度 Tick —— 所有行为的唯一入口
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.is(this)) return;

        int concentration = state.getValue(CONCENTRATION);
        boolean isSource = isSourceLevel(state);
        boolean inGrace = isInGracePeriod(level, pos);

        if (inGrace) {
            tickGracePeriod(state, level, pos, random, concentration, isSource);
        } else {
            tickDecay(state, level, pos, concentration, isSource);
        }
    }

    // ── 恩惠期 tick：扩散 + 销毁 ──────────────────────────────────

    private void tickGracePeriod(BlockState state, ServerLevel level, BlockPos pos,
                                  RandomSource random, int concentration, boolean isSource) {
        // 1. 销毁相邻方块
        destroyAdjacentBlocks(level, pos);

        // 2. 扩散到新位置
        if (concentration > 1) {
            spreadAggressive(level, pos, concentration, isSource);
        }

        // 3. 安排下一次恩惠期 tick
        scheduleNextTick(level, pos, true);
    }

    // ── 衰减期 tick：浓度递减 → 消失 ─────────────────────────────

    private void tickDecay(BlockState state, ServerLevel level, BlockPos pos,
                           int concentration, boolean isSource) {
        int baseDecay = Modconfigs.VOID_DECAY_PER_20T.get();
        // 非源方块（流动态）衰减更快
        int decay = isSource ? baseDecay : Math.max(baseDecay * 2, 2);

        int newConc = concentration - decay;
        if (newConc <= 0) {
            // 浓度归零 → 移除方块
            BIRTH_TIMES.remove(packKey(level, pos));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            // 降低浓度，继续调度衰减
            level.setBlock(pos, state.setValue(CONCENTRATION, newConc), Block.UPDATE_ALL);
            scheduleNextTick(level, pos, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  randomTick（兜底保障）
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean isRandomlyTicking(BlockState state) { return true; }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 恩惠期内 randomTick 补充一次销毁
        if (isInGracePeriod(level, pos)) {
            destroyAdjacentBlocks(level, pos);
        }

        // 如果没有挂起的调度 tick，重新安排（防止 tick 丢失）
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            boolean inGrace = isInGracePeriod(level, pos);
            scheduleNextTick(level, pos, inGrace);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  扩散逻辑（仅恩惠期内使用）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 积极扩散：在恩惠期内向周围方向扩散虚空流体。
     * <ul>
     *   <li>源方块：向全部 6 个方向扩散，每次最多 3 个方向</li>
     *   <li>非源方块：仅向下扩散</li>
     *   <li>新方块的浓度 = 当前浓度 - 1</li>
     * </ul>
     */
    private void spreadAggressive(ServerLevel level, BlockPos pos,
                                   int concentration, boolean isSource) {
        int maxRadius = Modconfigs.VOID_MAX_SPREAD_RADIUS.get();
        // 浓度太低时不再扩散（控制最大半径）
        if (concentration <= (15 - maxRadius)) return;

        Direction[] dirs = isSource
                ? new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH,
                                  Direction.EAST, Direction.WEST, Direction.UP}
                : new Direction[]{Direction.DOWN};

        int spreadCount = 0;
        int maxPerTick = isSource ? 3 : 1;

        for (Direction dir : dirs) {
            if (spreadCount >= maxPerTick) break;

            BlockPos target = pos.relative(dir);
            BlockState targetState = level.getBlockState(target);

            // 不扩散到基岩或已有的虚空流体
            if (targetState.is(Blocks.BEDROCK)) continue;
            if (targetState.getBlock() instanceof VoidFluidBlock) continue;

            // 需要是空气或可破坏的方块
            if (!targetState.isAir() && targetState.getDestroySpeed(level, target) < 0) continue;

            // 先清除目标位置的方块
            if (!targetState.isAir()) {
                level.removeBlock(target, false);
            }

            // 放置新的虚空流体（流动态，浓度递减）
            int newConc = Math.max(1, concentration - 1);
            BlockState newState = ModBlocks.VOID_FLUID_BLOCK.get().defaultBlockState()
                    .setValue(LEVEL, 6)            // 流动态
                    .setValue(CONCENTRATION, newConc);
            level.setBlock(target, newState, Block.UPDATE_ALL);
            // onPlace() 会自动记录出生时间并调度 tick
            spreadCount++;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  销毁逻辑（恩惠期内使用）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 销毁相邻方块：虚空流体的毁灭性效果。
     * 跳过基岩和其它虚空流体方块。
     */
    private static void destroyAdjacentBlocks(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);

            if (neighborState.isAir()) continue;
            if (neighborState.is(Blocks.BEDROCK)) continue;
            if (neighborState.getBlock() instanceof VoidFluidBlock) continue;

            level.removeBlock(neighbor, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  实体接触伤害
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;
        if (entity instanceof ItemEntity) { entity.discard(); return; }
        DamageSource voidDamage = new DamageSource(
                level.registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(VOID_FLUID_DAMAGE_TYPE));
        entity.hurt(voidDamage, Float.MAX_VALUE);
        if (entity.isAlive()) entity.kill();  // 安全兜底，绝不用 discard() 以免卡死
    }

    // ═══════════════════════════════════════════════════════════════
    //  恩惠期判断
    // ═══════════════════════════════════════════════════════════════

    private boolean isInGracePeriod(ServerLevel level, BlockPos pos) {
        return isBlockInGracePeriod(level, pos);
    }

    /**
     * 公共方法：检查指定位置的虚空流体方块是否仍在恩惠期内。
     * 可供外部（如 VoidFluid）调用，但当前设计中 VoidFluid 已不需要此判断。
     */
    public static boolean isBlockInGracePeriod(ServerLevel level, BlockPos pos) {
        long key = packKey(level, pos);
        Long birthTime = BIRTH_TIMES.get(key);
        if (birthTime == null) {
            // 没有出生记录（可能是区块重载后），重新注册并给予恩惠期
            BIRTH_TIMES.put(key, level.getGameTime());
            return true;
        }
        long age = level.getGameTime() - birthTime;
        return age < Modconfigs.VOID_GRACE_PERIOD.get();
    }

    // ═══════════════════════════════════════════════════════════════
    //  公共工具方法
    // ═══════════════════════════════════════════════════════════════

    /** 在指定位置放置虚空流体源方块（供爆炸等外部调用） */
    public static void placeAt(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (current.is(Blocks.BEDROCK)) return;
        if (current.getBlock() instanceof VoidFluidBlock) return;

        if (!current.isAir()) {
            level.removeBlock(pos, false);
        }
        BlockState state = ModBlocks.VOID_FLUID_BLOCK.get().defaultBlockState()
                .setValue(LEVEL, 8)
                .setValue(CONCENTRATION, 15);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        // onPlace() 会自动记录出生时间并调度 tick
    }

    /** 清理所有缓存的出生时间（服务器关闭时调用） */
    public static void clearBirthTimes() {
        BIRTH_TIMES.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    //  内部工具
    // ═══════════════════════════════════════════════════════════════

    /** 判断方块状态是否为源方块（LEVEL == 0 或 >= 8） */
    private static boolean isSourceLevel(BlockState state) {
        int level = state.getValue(LEVEL);
        return level == 0 || level >= 8;
    }

    /** 调度下一次自定义 tick */
    private void scheduleNextTick(Level level, BlockPos pos, boolean inGracePeriod) {
        if (level.getBlockTicks().hasScheduledTick(pos, this)) return; // 避免重复调度
        int interval = inGracePeriod
                ? Modconfigs.VOID_SPREAD_INTERVAL.get()
                : Modconfigs.VOID_DECAY_INTERVAL.get();
        level.scheduleTick(pos, this, interval);
    }

    /** 维度 + 坐标 → 唯一 key */
    private static long packKey(Level level, BlockPos pos) {
        return pos.asLong() ^ ((long) level.dimension().location().hashCode() * 0x9E3779B97F4A7C15L);
    }
}
