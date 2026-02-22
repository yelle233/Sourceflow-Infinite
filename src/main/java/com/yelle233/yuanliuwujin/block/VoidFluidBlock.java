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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚空流体方块（v3.0 重构）。
 * <p>
 * 设计思路：
 * <ul>
 *   <li>扩散 / 吞噬固体方块由 {@link com.yelle233.yuanliuwujin.fluid.VoidFluid} 的原版 tick 驱动，
 *       不再在方块层面实现扩散，彻底解决桶取走后幽灵贴图的问题。</li>
 *   <li>恩惠期（grace period）：仅对<b>源方块</b>（LEVEL=0）计时。
 *       恩惠期结束后移除源方块；流动方块失去源头后通过原版流体逻辑自然消失。</li>
 *   <li>随机 tick（{@link #randomTick}）：随机销毁相邻非虚空流体方块，
 *       模拟"吞噬方块"行为，速度比扩散慢（randomTick 频率约 1/68 每 tick），
 *       防止方块还没扩散到就被提前吞噬。</li>
 *   <li>实体接触（{@link #entityInside}）：根据配置决定是否吞噬物品/杀死实体。</li>
 * </ul>
 */
public class VoidFluidBlock extends LiquidBlock {

    /**
     * 记录虚空流体<b>源方块</b>的"出生时间"（gameTime），用于判断恩惠期是否结束。
     * key = dimension hash XOR pos.asLong()，value = 出生时的 gameTime。
     * 源方块被移除时清理对应条目。
     */
    private static final Map<Long, Long> BIRTH_TIMES = new ConcurrentHashMap<>();

    public VoidFluidBlock(net.minecraft.world.level.material.FlowingFluid fluid,
                          BlockBehaviour.Properties properties) {
        super(fluid, properties);
        // LEVEL=0 表示源方块（原版 LiquidBlock 语义）
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 0));
    }

    // ═══════════════════════════════════════════════════════════
    //  生命周期
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide) {
            long key = packKey(level, pos);
            // 源方块和流动方块都记录出生时间（流动方块复用已有记录）
            if (!BIRTH_TIMES.containsKey(key)) {
                BIRTH_TIMES.put(key, level.getGameTime());
            }
            // 所有虚空流体方块都安排清除 tick
            if (level instanceof ServerLevel serverLevel
                    && !serverLevel.getBlockTicks().hasScheduledTick(pos, this)) {
                long birthTime = BIRTH_TIMES.getOrDefault(key, level.getGameTime());
                long remaining = Math.max(1,
                        Modconfigs.VOID_GRACE_PERIOD.get() - (level.getGameTime() - birthTime));
                level.scheduleTick(pos, this, (int) remaining);
            }
        }
    }



    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide) {
            boolean wasReplacedByVoid = newState.getBlock() instanceof VoidFluidBlock;
            if (!wasReplacedByVoid) {
                BIRTH_TIMES.remove(packKey(level, pos));
            }
        }
    }


    // ═══════════════════════════════════════════════════════════
    //  恩惠期：源方块到期后自我移除
    // ═══════════════════════════════════════════════════════════

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.is(this)) return;

        long key = packKey(level, pos);
        Long birthTime = BIRTH_TIMES.get(key);
        long gracePeriod = Modconfigs.VOID_GRACE_PERIOD.get();

        if (birthTime == null || (level.getGameTime() - birthTime) >= gracePeriod) {
            BIRTH_TIMES.remove(key);
            // 【修复】不能用 level.removeBlock()！removeBlock 的实现是
            // level.setBlock(pos, fluidState.createLegacyBlock(), 3)
            // 而此处的 fluidState 就是虚空流体本身，导致"移除"后又立刻放回来。
            // 必须显式设置为 AIR 才能真正移除。
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
        } else {
            long remaining = Math.max(1, gracePeriod - (level.getGameTime() - birthTime));
            level.scheduleTick(pos, this, (int) remaining);
        }
    }


    // ═══════════════════════════════════════════════════════════
    //  随机 tick：方块吞噬（慢于扩散，防止吞噬超前）
    // ═══════════════════════════════════════════════════════════

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!Modconfigs.VOID_DESTROY_BLOCKS.get()) return;

        // 随机选一个方向尝试吞噬方块
        Direction[] dirs = Direction.values();
        Direction dir = dirs[random.nextInt(dirs.length)];
        BlockPos neighbor = pos.relative(dir);
        BlockState neighborState = level.getBlockState(neighbor);

        if (neighborState.isAir()) return;
        if (neighborState.is(Blocks.BEDROCK)) return;
        if (neighborState.getBlock() instanceof VoidFluidBlock) return;
        if (neighborState.getDestroySpeed(level, neighbor) < 0) return;

        level.destroyBlock(neighbor, false); // 无掉落
    }

    // ═══════════════════════════════════════════════════════════
    //  实体接触
    // ═══════════════════════════════════════════════════════════

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;

        if (entity instanceof ItemEntity) {
            if (Modconfigs.VOID_KILL_ITEMS.get()) entity.discard();
            return;
        }

        if (Modconfigs.VOID_KILL_ENTITIES.get()) {
            DamageSource voidDamage = level.damageSources().fellOutOfWorld();
            entity.hurt(voidDamage, Float.MAX_VALUE);
            if (entity.isAlive()) entity.discard();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  公共工具方法
    // ═══════════════════════════════════════════════════════════

    /**
     * 在指定位置放置虚空流体源方块（供爆炸等触发时调用）。
     */
    public static void placeAt(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (current.is(Blocks.BEDROCK)) return;
        if (current.getBlock() instanceof VoidFluidBlock) return;
        if (!current.isAir() && current.getDestroySpeed(level, pos) < 0) return;

        if (!current.isAir()) {
            level.destroyBlock(pos, false);
        }
        // LEVEL=0 → 源方块（默认状态）
        BlockState state = ModBlocks.VOID_FLUID_BLOCK.get().defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_ALL);
        // onPlace 会自动记录出生时间并安排恩惠期 tick
    }

    /** 清理所有出生时间记录（服务器关闭时调用） */
    public static void clearBirthTimes() {
        BIRTH_TIMES.clear();
    }

    /** 获取指定位置的出生时间（供 VoidFluid 流体 tick 调用） */
    @javax.annotation.Nullable
    public static Long getBirthTime(long key) {
        return BIRTH_TIMES.get(key);
    }

    /** 移除指定位置的出生时间记录 */
    public static void removeBirthTime(long key) {
        BIRTH_TIMES.remove(key);
    }

    /** 公开版本的 packKey（供 VoidFluid 使用） */
    public static long makeKey(Level level, BlockPos pos) {
        return packKey(level, pos);
    }

    /** 维度 + 坐标 → 唯一 key */
    private static long packKey(Level level, BlockPos pos) {
        return pos.asLong() ^ ((long) level.dimension().location().hashCode() * 0x9E3779B97F4A7C15L);
    }
}
