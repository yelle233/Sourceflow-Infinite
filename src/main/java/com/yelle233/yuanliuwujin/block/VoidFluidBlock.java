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
import net.minecraft.world.level.material.FlowingFluid;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚空流体方块（1.20.1 Forge 版本）。
 * 与 1.21.1 版本功能一致：恩惠期机制、随机 tick 吞噬方块、实体接触效果。
 */
public class VoidFluidBlock extends LiquidBlock {

    private static final Map<Long, Long> BIRTH_TIMES = new ConcurrentHashMap<>();

    public VoidFluidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            long key = packKey(level, pos);
            if (!BIRTH_TIMES.containsKey(key)) {
                BIRTH_TIMES.put(key, level.getGameTime());
            }
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

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.is(this)) return;
        long key = packKey(level, pos);
        Long birthTime = BIRTH_TIMES.get(key);
        long gracePeriod = Modconfigs.VOID_GRACE_PERIOD.get();

        if (birthTime == null || (level.getGameTime() - birthTime) >= gracePeriod) {
            BIRTH_TIMES.remove(key);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            long remaining = Math.max(1, gracePeriod - (level.getGameTime() - birthTime));
            level.scheduleTick(pos, this, (int) remaining);
        }
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!Modconfigs.VOID_DESTROY_BLOCKS.get()) return;

        Direction[] dirs = Direction.values();
        Direction dir = dirs[random.nextInt(dirs.length)];
        BlockPos neighbor = pos.relative(dir);
        BlockState neighborState = level.getBlockState(neighbor);

        if (neighborState.isAir()) return;
        if (neighborState.is(Blocks.BEDROCK)) return;
        if (neighborState.getBlock() instanceof VoidFluidBlock) return;
        if (neighborState.getDestroySpeed(level, neighbor) < 0) return;

        if (!neighborState.getFluidState().isEmpty()) {
            level.setBlock(neighbor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            level.destroyBlock(neighbor, false);
        }
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;
        if (entity instanceof ItemEntity) {
            if (Modconfigs.VOID_KILL_ITEMS.get()) entity.discard();
            return;
        }
        if (Modconfigs.VOID_KILL_ENTITIES.get()) {
            // 1.20.1 使用 DamageSource 的旧 API
            entity.hurt(level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            if (entity.isAlive()) entity.discard();
        }
    }

    // ═══ 公共工具方法 ═══

    public static void placeAt(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (current.is(Blocks.BEDROCK)) return;
        if (current.getBlock() instanceof VoidFluidBlock) return;
        if (!current.isAir() && current.getDestroySpeed(level, pos) < 0) return;

        if (!current.isAir()) level.destroyBlock(pos, false);

        BlockState state = ModBlocks.VOID_FLUID_BLOCK.get().defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    public static void clearBirthTimes() { BIRTH_TIMES.clear(); }

    @Nullable
    public static Long getBirthTime(long key) { return BIRTH_TIMES.get(key); }

    public static void removeBirthTime(long key) { BIRTH_TIMES.remove(key); }

    public static long makeKey(Level level, BlockPos pos) { return packKey(level, pos); }

    private static long packKey(Level level, BlockPos pos) {
        return pos.asLong() ^ ((long) level.dimension().location().hashCode() * 0x9E3779B97F4A7C15L);
    }
}

