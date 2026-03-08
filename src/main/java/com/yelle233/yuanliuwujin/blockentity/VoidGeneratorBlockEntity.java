package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.VoidGeneratorBlock;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nonnull;
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
    private boolean hadRedstoneSignal = false;
    private final EnumMap<Direction, Boolean> savedSideOutputs = new EnumMap<>(Direction.class);

    private final FluidTank voidTank;
    private final EnergyStorage energyStorage;

    private int lastTickFEGenerated = 0;
    private int lastTickVoidConsumed = 0;

    public VoidGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VOID_GENERATOR.get(), pos, state);

        int capacity = Modconfigs.MACHINE_VOID_TANK_CAPACITY.get();
        this.voidTank = new FluidTank(capacity) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }

            @Override
            public boolean isFluidValid(FluidStack stack) {
                return stack.getFluid().isSame(ModFluids.VOID_FLUID_SOURCE.get());
            }
        };

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

        for (Direction dir : Direction.values()) {
            sideOutputs.put(dir, false);
            sideRates.put(dir, 1);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VoidGeneratorBlockEntity be) {
        if (level.isClientSide) return;

        boolean dirty = false;

        // 从底部容器抽取虚空流体
        if (be.voidTank.getFluidAmount() < be.voidTank.getCapacity()) {
            BlockEntity below = level.getBlockEntity(pos.below());
            if (below != null) {
                LazyOptional<IFluidHandler> cap = below.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP);
                cap.ifPresent(handler -> {
                    FluidStack drained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
                    if (!drained.isEmpty() && drained.getFluid().isSame(ModFluids.VOID_FLUID_SOURCE.get())) {
                        int filled = be.voidTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                        if (filled > 0) {
                            handler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                        }
                    }
                });
            }
        }

        // 计算总输出速率
        long totalOutputRate = 0;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            if (be.sideOutputs.get(dir)) {
                totalOutputRate += be.sideRates.get(dir);
            }
        }

        int currentEnergy = be.energyStorage.getEnergyStored();
        int maxEnergy = be.energyStorage.getMaxEnergyStored();

        if (be.voidTank.getFluidAmount() > 0 && currentEnergy < maxEnergy) {
            long energyNeeded = totalOutputRate;
            if (currentEnergy < maxEnergy) {
                int bufferSpace = maxEnergy - currentEnergy;
                if (totalOutputRate == 0) {
                    energyNeeded = bufferSpace;
                } else {
                    energyNeeded = Math.min(totalOutputRate + bufferSpace, totalOutputRate * 2);
                }
            }

            if (energyNeeded > 0) {
                int ratio = Modconfigs.VOID_TO_FE_RATIO.get();
                double voidNeededExact = (double) energyNeeded / ratio;
                int voidAvailable = be.voidTank.getFluidAmount();

                if (voidNeededExact >= 1.0) {
                    long voidNeededLong = (long) Math.ceil(voidNeededExact);
                    int voidNeeded = (int) Math.min(voidNeededLong, Integer.MAX_VALUE);
                    int voidToConsume = Math.min(voidNeeded, voidAvailable);

                    if (voidToConsume > 0) {
                        be.voidTank.drain(voidToConsume, IFluidHandler.FluidAction.EXECUTE);
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
                    int energyToStore = (int) Math.min(energyNeeded, Integer.MAX_VALUE);
                    int feActuallyStored = be.energyStorage.receiveEnergy(energyToStore, false);
                    be.lastTickFEGenerated = feActuallyStored;

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
            be.lastTickFEGenerated = 0;
            be.lastTickVoidConsumed = 0;
        }

        // 输出能量到相邻方块
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            if (!be.sideOutputs.get(dir)) continue;

            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
            if (neighbor != null) {
                LazyOptional<net.minecraftforge.energy.IEnergyStorage> cap =
                    neighbor.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite());
                cap.ifPresent(handler -> {
                    if (handler.canReceive()) {
                        int rate = be.sideRates.get(dir);
                        int extracted = be.energyStorage.extractEnergy(rate, false);
                        if (extracted > 0) {
                            int received = handler.receiveEnergy(extracted, false);
                            if (received < extracted) {
                                be.energyStorage.receiveEnergy(extracted - received, false);
                            }
                        }
                    }
                });
            }
        }

        // 更新方块状态
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

    @Override
    public void toggleSideOutput(Direction dir) {
        if (dir == Direction.DOWN) return;
        sideOutputs.put(dir, !sideOutputs.get(dir));
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public boolean getSideOutput(Direction dir) {
        return sideOutputs.getOrDefault(dir, false);
    }

    @Override
    public void adjustSideRate(Direction dir, int delta) {
        if (dir == Direction.DOWN) return;
        int current = sideRates.get(dir);
        int newRate = Math.max(1, Math.min(Integer.MAX_VALUE - 1, current + delta));
        sideRates.put(dir, newRate);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public int getSideRate(Direction dir) {
        return sideRates.getOrDefault(dir, 1);
    }

    // ── 红石控制 ──

    public void toggleRedstoneControl() {
        boolean anyEnabled = sideOutputs.values().stream().anyMatch(b -> b);

        if (anyEnabled) {
            for (Direction dir : Direction.values()) {
                if (dir == Direction.DOWN) continue;
                savedSideOutputs.put(dir, sideOutputs.get(dir));
                sideOutputs.put(dir, false);
            }
        } else {
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.put("VoidTank", voidTank.writeToNBT(new CompoundTag()));
        tag.putInt("Energy", energyStorage.getEnergyStored());

        CompoundTag sidesTag = new CompoundTag();
        for (Direction dir : Direction.values()) {
            sidesTag.putBoolean(dir.getName() + "_output", sideOutputs.get(dir));
            sidesTag.putInt(dir.getName() + "_rate", sideRates.get(dir));
        }
        tag.put("Sides", sidesTag);

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
    public void load(CompoundTag tag) {
        super.load(tag);

        if (tag.contains("VoidTank")) {
            voidTank.readFromNBT(tag.getCompound("VoidTank"));
        }

        if (tag.contains("Energy")) {
            energyStorage.extractEnergy(energyStorage.getEnergyStored(), false);
            energyStorage.receiveEnergy(tag.getInt("Energy"), false);
        }

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
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            if (side == Direction.DOWN) return LazyOptional.empty();
            return LazyOptional.of(() -> energyStorage).cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (side == Direction.DOWN) {
                return LazyOptional.of(() -> voidTank).cast();
            }
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }
}
