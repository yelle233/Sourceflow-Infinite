package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.VoidGeneratorBlock;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nullable;
import java.util.EnumMap;

/**
 * 虚空发电机方块实体
 *
 * 底部输入虚空流体，其余五个面输出电量
 * 默认 1 mB 虚空流体 = 10 FE
 */
public class VoidGeneratorBlockEntity extends BlockEntity implements IVoidGenerator {

    // 侧面输出状态（true = 开启，false = 关闭）
    private final EnumMap<Direction, Boolean> sideOutputs = new EnumMap<>(Direction.class);
    // 侧面发电速率（FE/t）
    private final EnumMap<Direction, Integer> sideRates = new EnumMap<>(Direction.class);

    // ── 红石控制 ──
    private boolean hadRedstoneSignal = false; // 上一次的红石信号状态
    private final EnumMap<Direction, Boolean> savedSideOutputs = new EnumMap<>(Direction.class); // 保存的输出状态

    private final FluidTank voidTank;
    private final EnergyStorage energyStorage;

    private int lastTickFEGenerated = 0;
    private int lastTickVoidConsumed = 0; // 上一 tick 消耗的虚空流体量（mB）
    private int secondTick = 0;

    public VoidGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VOID_GENERATOR.get(), pos, state);

        // 初始化虚空流体储罐
        int capacity = Modconfigs.MACHINE_VOID_TANK_CAPACITY.get();
        this.voidTank = new FluidTank(capacity) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return stack.getFluid().isSame(ModFluids.VOID_FLUID_SOURCE.get());
            }
        };

        // 初始化能量存储（容量 Integer.MAX_VALUE FE，可接收和提取）
        this.energyStorage = new EnergyStorage(1000000, Integer.MAX_VALUE, Integer.MAX_VALUE) {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                int received = super.receiveEnergy(maxReceive, simulate);
                if (!simulate && received > 0) setChanged();
                return received;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                int extracted = super.extractEnergy(maxExtract, simulate);
                if (!simulate && extracted > 0) setChanged();
                return extracted;
            }
        };

        // 初始化侧面配置（所有面默认关闭，速率 1 FE/t）
        for (Direction dir : Direction.values()) {
            sideOutputs.put(dir, false); // 默认关闭
            sideRates.put(dir, 1); // 默认 1 FE/t
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Tick 逻辑
    // ══════════════════════════════════════════════════════════════

    public static void tick(Level level, BlockPos pos, BlockState state, VoidGeneratorBlockEntity be) {
        if (level.isClientSide) return;

        boolean dirty = false;

        // 从底部容器抽取虚空流体（最高速）
        if (be.voidTank.getFluidAmount() < be.voidTank.getCapacity()) {
            BlockEntity below = level.getBlockEntity(pos.below());
            if (below != null) {
                var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.below(), Direction.UP);
                if (handler != null) {
                    // 尝试抽取最大量
                    FluidStack drained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
                    if (!drained.isEmpty() && drained.getFluid().isSame(ModFluids.VOID_FLUID_SOURCE.get())) {
                        int filled = be.voidTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                        if (filled > 0) {
                            handler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                            dirty = true;
                        }
                    }
                }
            }
        }

        // 计算本 tick 需要的总输出量（使用 long 防止溢出）
        long totalOutputRate = 0;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            if (be.sideOutputs.get(dir)) {
                totalOutputRate += be.sideRates.get(dir);
            }
        }

        // 发电逻辑：持续发电以满足输出需求
        int currentEnergy = be.energyStorage.getEnergyStored();
        int maxEnergy = be.energyStorage.getMaxEnergyStored();

        if (be.voidTank.getFluidAmount() > 0 && currentEnergy < maxEnergy) {
            // 计算需要发电的量：输出需求 + 缓冲补充（如果缓冲未满）
            long energyNeeded = totalOutputRate;
            if (currentEnergy < maxEnergy) {
                // 缓冲未满，额外发电以填充缓冲（最多发电到满）
                int bufferSpace = maxEnergy - currentEnergy;
                // 如果没有输出需求，以最大速率发电填充缓冲
                if (totalOutputRate == 0) {
                    energyNeeded = bufferSpace; // 全速发电
                } else {
                    energyNeeded = Math.min(totalOutputRate + bufferSpace, totalOutputRate * 2); // 最多发电 2 倍输出速率
                }
            }

            if (energyNeeded > 0) {
                // 计算需要消耗的虚空流体量
                int ratio = Modconfigs.VOID_TO_FE_RATIO.get(); // 默认 10 FE = 1 mB
                double voidNeededExact = (double) energyNeeded / ratio;

                int voidAvailable = be.voidTank.getFluidAmount();

                if (voidNeededExact >= 1.0) {
                    // 需要至少 1 mB（限制在 int 范围内防止溢出）
                    long voidNeededLong = (long) Math.ceil(voidNeededExact);
                    int voidNeeded = (int) Math.min(voidNeededLong, Integer.MAX_VALUE);
                    int voidToConsume = Math.min(voidNeeded, voidAvailable);

                    if (voidToConsume > 0) {
                        // 消耗虚空流体
                        be.voidTank.drain(voidToConsume, IFluidHandler.FluidAction.EXECUTE);

                        // 产生能量（按照实际消耗的虚空流体计算）
                        long feProduced = (long) voidToConsume * ratio;
                        int energyToStore = (int) Math.min(feProduced, Integer.MAX_VALUE);
                        int feActuallyStored = be.energyStorage.receiveEnergy(energyToStore, false);
                        be.lastTickFEGenerated = feActuallyStored;
                        be.lastTickVoidConsumed = voidToConsume;
                        dirty = true;
                    } else {
                        be.lastTickFEGenerated = 0;
                        be.lastTickVoidConsumed = 0;
                    }
                } else if (voidAvailable > 0) {
                    // 需要小于 1 mB（低速率情况）
                    int energyToStore = (int) Math.min(energyNeeded, Integer.MAX_VALUE);
                    int feActuallyStored = be.energyStorage.receiveEnergy(energyToStore, false);
                    be.lastTickFEGenerated = feActuallyStored;

                    // 每 ratio tick 消耗 1 mB
                    if (level.getGameTime() % ratio == 0) {
                        be.voidTank.drain(1, IFluidHandler.FluidAction.EXECUTE);
                        be.lastTickVoidConsumed = 1;
                    } else {
                        be.lastTickVoidConsumed = 0;
                    }
                    dirty = true;
                } else {
                    be.lastTickFEGenerated = 0;
                    be.lastTickVoidConsumed = 0;
                }
            } else {
                be.lastTickFEGenerated = 0;
                be.lastTickVoidConsumed = 0;
            }
        } else {
            // 无虚空流体或能量已满，停止发电
            be.lastTickFEGenerated = 0;
            be.lastTickVoidConsumed = 0;
        }

        // 输出能量到相邻方块
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue; // 底部不输出
            if (!be.sideOutputs.get(dir)) continue; // 侧面关闭

            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
            if (neighbor != null) {
                var handler = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
                if (handler != null && handler.canReceive()) {
                    int rate = be.sideRates.get(dir);
                    int extracted = be.energyStorage.extractEnergy(rate, false);
                    if (extracted > 0) {
                        int received = handler.receiveEnergy(extracted, false);
                        if (received < extracted) {
                            // 如果对方没有完全接收，退还能量
                            be.energyStorage.receiveEnergy(extracted - received, false);
                        }
                        if (received > 0) dirty = true;
                    }
                }
            }
        }

        // 更新方块状态（LIT）：只有虚空储罐和能量都为 0 时才不发光
        boolean shouldLit = !(be.voidTank.getFluidAmount() == 0 && be.energyStorage.getEnergyStored() == 0);
        if (state.getValue(VoidGeneratorBlock.LIT) != shouldLit) {
            level.setBlock(pos, state.setValue(VoidGeneratorBlock.LIT, shouldLit), 3);
            dirty = true;
        }

        if (dirty) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /**
     * 计算每 tick 最大发电量（所有开启的侧面速率之和）
     * 如果没有开启的面，返回一个基础发电速率（1 FE/t）以便内部缓冲
     */
    private int calculateMaxFEPerTick() {
        int total = 0;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            if (sideOutputs.get(dir)) {
                total += sideRates.get(dir);
            }
        }
        // 如果没有开启的面，至少以 1 FE/t 的速率发电（用于内部缓冲）
        return Math.max(total, 1);
    }

    // ══════════════════════════════════════════════════════════════
    //  侧面配置（实现 IVoidGenerator 接口）
    // ══════════════════════════════════════════════════════════════

    /**
     * 切换侧面输出状态
     */
    @Override
    public void toggleSideOutput(Direction dir) {
        if (dir == Direction.DOWN) return; // 底部不能切换
        sideOutputs.put(dir, !sideOutputs.get(dir));
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * 获取侧面输出状态
     */
    @Override
    public boolean getSideOutput(Direction dir) {
        return sideOutputs.getOrDefault(dir, false);
    }

    /**
     * 调整侧面发电速率
     */
    @Override
    public void adjustSideRate(Direction dir, int delta) {
        if (dir == Direction.DOWN) return; // 底部不能调整
        int current = sideRates.get(dir);
        int newRate = Math.max(1, Math.min(Integer.MAX_VALUE - 1, current + delta));
        sideRates.put(dir, newRate);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * 获取侧面发电速率
     */
    @Override
    public int getSideRate(Direction dir) {
        return sideRates.getOrDefault(dir, 1);
    }

    // ══════════════════════════════════════════════════════════════
    //  红石控制
    // ══════════════════════════════════════════════════════════════

    /**
     * 红石信号触发：切换所有输出面的开关状态
     */
    public void toggleRedstoneControl() {
        boolean anyEnabled = sideOutputs.values().stream().anyMatch(b -> b);

        if (anyEnabled) {
            // 当前有面开启 -> 保存状态并全部关闭
            for (Direction dir : Direction.values()) {
                if (dir == Direction.DOWN) continue;
                savedSideOutputs.put(dir, sideOutputs.get(dir));
                sideOutputs.put(dir, false);
            }
        } else {
            // 当前全部关闭 -> 恢复保存的状态
            for (Direction dir : Direction.values()) {
                if (dir == Direction.DOWN) continue;
                Boolean saved = savedSideOutputs.get(dir);
                if (saved != null) {
                    sideOutputs.put(dir, saved);
                }
            }
        }

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hadRedstoneSignal() {
        return hadRedstoneSignal;
    }

    public void setRedstoneSignal(boolean signal) {
        this.hadRedstoneSignal = signal;
        setChanged();
    }

    // ══════════════════════════════════════════════════════════════
    //  Getters
    // ══════════════════════════════════════════════════════════════

    public FluidTank getVoidTank() {
        return voidTank;
    }

    public EnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public int getLastTickFEGenerated() {
        return lastTickFEGenerated;
    }

    public int getLastTickVoidConsumed() {
        return lastTickVoidConsumed;
    }

    // ══════════════════════════════════════════════════════════════
    //  NBT 序列化
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);

        // 保存虚空流体储罐
        tag.put("VoidTank", voidTank.writeToNBT(provider, new CompoundTag()));

        // 保存能量
        tag.putInt("Energy", energyStorage.getEnergyStored());

        // 保存侧面配置
        CompoundTag sidesTag = new CompoundTag();
        for (Direction dir : Direction.values()) {
            sidesTag.putBoolean(dir.getName() + "_output", sideOutputs.get(dir));
            sidesTag.putInt(dir.getName() + "_rate", sideRates.get(dir));
        }
        tag.put("Sides", sidesTag);

        // 保存红石控制状态
        tag.putBoolean("HadRedstoneSignal", hadRedstoneSignal);
        CompoundTag savedTag = new CompoundTag();
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            Boolean saved = savedSideOutputs.get(dir);
            if (saved != null) {
                savedTag.putBoolean(dir.getName(), saved);
            }
        }
        tag.put("SavedOutputs", savedTag);

        tag.putInt("LastTickFE", lastTickFEGenerated);
        tag.putInt("LastTickVoid", lastTickVoidConsumed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        // 加载虚空流体储罐
        if (tag.contains("VoidTank")) {
            voidTank.readFromNBT(provider, tag.getCompound("VoidTank"));
        }

        // 加载能量（先提取所有能量，再接收保存的值）
        if (tag.contains("Energy")) {
            energyStorage.extractEnergy(energyStorage.getEnergyStored(), false);
            energyStorage.receiveEnergy(tag.getInt("Energy"), false);
        }

        // 加载侧面配置
        if (tag.contains("Sides")) {
            CompoundTag sidesTag = tag.getCompound("Sides");
            for (Direction dir : Direction.values()) {
                if (sidesTag.contains(dir.getName() + "_output")) {
                    sideOutputs.put(dir, sidesTag.getBoolean(dir.getName() + "_output"));
                }
                if (sidesTag.contains(dir.getName() + "_rate")) {
                    sideRates.put(dir, sidesTag.getInt(dir.getName() + "_rate"));
                }
            }
        }

        // 加载红石控制状态
        if (tag.contains("HadRedstoneSignal")) {
            hadRedstoneSignal = tag.getBoolean("HadRedstoneSignal");
        }
        if (tag.contains("SavedOutputs")) {
            CompoundTag savedTag = tag.getCompound("SavedOutputs");
            for (Direction dir : Direction.values()) {
                if (dir == Direction.DOWN) continue;
                if (savedTag.contains(dir.getName())) {
                    savedSideOutputs.put(dir, savedTag.getBoolean(dir.getName()));
                }
            }
        }

        if (tag.contains("LastTickFE")) {
            lastTickFEGenerated = tag.getInt("LastTickFE");
        }
        if (tag.contains("LastTickVoid")) {
            lastTickVoidConsumed = tag.getInt("LastTickVoid");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
