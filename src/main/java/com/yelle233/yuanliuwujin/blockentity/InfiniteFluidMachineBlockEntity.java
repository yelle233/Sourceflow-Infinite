package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.InfiniteFluidMachineBlock;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.*;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.BindType;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.EnumMap;

/**
 * 无限流体机器方块实体（1.20.1 Forge 版本）。
 * <p>
 * 核心变化：使用 Forge 的 {@link LazyOptional} + {@code getCapability()} 暴露能力，
 * 替代 NeoForge 的 {@code RegisterCapabilitiesEvent} 注册方式。
 */
public class InfiniteFluidMachineBlockEntity extends BlockEntity {

    /* ====== 类成员变量 ====== */

    private boolean lastTickCanWork = false;
    private long chemPullBudgetRemaining = 0;
    private int pullBudgetRemaining = 0;
    //缓存化学品的 LazyOptional
    private LazyOptional<?> cachedGasCap, cachedInfusionCap, cachedPigmentCap, cachedSlurryCap;

    /* ====== 面模式枚举 ====== */

    public enum SideMode {
        OFF, PULL, BOTH
    }

    /* ====== 面模式管理 ====== */

    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);

    public SideMode getSideMode(Direction dir) {
        return dir == Direction.UP ? SideMode.OFF : sideModes.getOrDefault(dir, SideMode.OFF);
    }

    public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP) return;
        SideMode old = getSideMode(dir);
        SideMode next = switch (old) {
            case OFF  -> SideMode.PULL;
            case PULL -> SideMode.BOTH;
            case BOTH -> SideMode.OFF;
        };
        sideModes.put(dir, next);

        boolean wasEnabled = (old != SideMode.OFF);
        boolean nowEnabled = (next != SideMode.OFF);

        if (wasEnabled != nowEnabled) {
            // 可用性发生了变化（OFF↔非OFF），需要完整刷新 Capability
            notifyCapabilityChanged(dir);
        } else {
            // PULL↔BOTH 切换，Capability 不变，仅同步状态和渲染
            notifyStateOnly(dir);
        }
    }

    private void notifyStateOnly(Direction side) {
        if (level == null || level.isClientSide) return;
        setChanged();

        BlockState st = getBlockState();
        if (st.getBlock() instanceof InfiniteFluidMachineBlock) {
            boolean dirty = st.getValue(InfiniteFluidMachineBlock.DIRTY);
            st = st.setValue(InfiniteFluidMachineBlock.DIRTY, !dirty);
            level.setBlock(worldPosition, st, 3);
        }

        level.sendBlockUpdated(worldPosition, st, st, 3);
    }

    /* ====== 核心槽 ====== */

    private final ItemStackHandler coreSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            onCoreChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.getItem() instanceof InfiniteCoreItem;
        }
    };

    public ItemStackHandler getCoreSlot() {
        return coreSlot;
    }

    /* ====== 绑定类型查询 ====== */

    public BindType getCoreBindType() {
        ItemStack core = coreSlot.getStackInSlot(0);
        if (core.isEmpty()) return BindType.NONE;
        return InfiniteCoreItem.getBindType(core);
    }

    @Nullable
    public ResourceLocation getBoundChemicalId() {
        ItemStack core = coreSlot.getStackInSlot(0);
        if (core.isEmpty()) return null;
        return InfiniteCoreItem.getBoundChemical(core);
    }

    /* ====== 无限流体输出 Handler ====== */

    private final IFluidHandler infiniteOutput = new IFluidHandler() {
        @Override public int getTanks() { return canWorkNow() ? 1 : 0; }

        @Override public @NotNull FluidStack getFluidInTank(int tank) {
            if (!canWorkNow()) return FluidStack.EMPTY;
            Fluid f = getBoundSourceFluid();
            return f == null ? FluidStack.EMPTY : new FluidStack(f, Integer.MAX_VALUE);
        }

        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }

        @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (!canWorkNow()) return FluidStack.EMPTY;
            Fluid f = getBoundSourceFluid();
            if (f == null || resource.isEmpty() || resource.getFluid() != f) return FluidStack.EMPTY;

            int amt = Math.min(resource.getAmount(), pullBudgetRemaining);
            if (amt <= 0) return FluidStack.EMPTY;

            if (action.execute()) pullBudgetRemaining -= amt;
            return new FluidStack(f, amt);
        }

        @Override public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (!canWorkNow()) return FluidStack.EMPTY;
            Fluid f = getBoundSourceFluid();
            if (f == null || maxDrain <= 0) return FluidStack.EMPTY;

            int amt = Math.min(maxDrain, pullBudgetRemaining);
            if (amt <= 0) return FluidStack.EMPTY;

            if (action.execute()) pullBudgetRemaining -= amt;
            return new FluidStack(f, amt);
        }
    };


    /* ====== 能量系统 ====== */

    private static final int ENERGY_CAPACITY = 1_000_000_000;
    private int energy = 0;

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            int received = Math.min(ENERGY_CAPACITY - energy, maxReceive);
            if (!simulate && received > 0) {
                energy += received;
                markEnergyDirtyForSync();
                setChanged();
            }
            return received;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return ENERGY_CAPACITY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    public IEnergyStorage getEnergyStorage() { return energyStorage; }

    public boolean consumeFe(int cost) {
        if (cost <= 0) return true;
        if (energy < cost) return false;
        energy -= cost;
        markEnergyDirtyForSync();
        setChanged();
        return true;
    }

    public int calcFePerTick() {
        if (coreSlot.getStackInSlot(0).isEmpty()) return 0;
        BindType type = getCoreBindType();
        if (type == BindType.FLUID) {
            if (getBoundSourceFluid() == null) return 0;
        } else if (type == BindType.CHEMICAL) {
            if (getBoundChemicalId() == null) return 0;
        } else {
            return 0;
        }
        return Modconfigs.FE_PER_TICK.get() + countEnabledFaces() * Modconfigs.FE_PER_ENABLED_FACE_PER_TICK.get();
    }

    public int getFeCostPerTick() { return calcFePerTick(); }

    public boolean canWorkNow() {
        int cost = calcFePerTick();
        return cost > 0 && energy >= cost;
    }

    public int countEnabledFaces() {
        int count = 0;
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            SideMode m = getSideMode(d);
            if (m == SideMode.PULL || m == SideMode.BOTH) count++;
        }
        return count;
    }

    public boolean hasValidBinding() {
        if (coreSlot.getStackInSlot(0).isEmpty()) return false;
        BindType type = getCoreBindType();
        if (type == BindType.FLUID) return getBoundSourceFluid() != null;
        if (type == BindType.CHEMICAL) return getBoundChemicalId() != null;
        return false;
    }

    /* ====== Forge Capabilities（LazyOptional） ====== */

    private LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> infiniteOutput);
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyStorage);
    // 化学品能力通过 MekChemicalHelper 中的 Capability 引用

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // 能量：仅顶面
        if (cap == ForgeCapabilities.ENERGY) {
            if (side == null || side == Direction.UP) return energyCap.cast();
            return LazyOptional.empty();
        }

        // 流体：侧面 + 绑定流体 + PULL/BOTH
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (!hasValidBinding()) return LazyOptional.empty();
            if (getCoreBindType() != BindType.FLUID) return LazyOptional.empty();

            // 顶面永远不提供
            if (side == Direction.UP) return LazyOptional.empty();

            // side == null：只要任意侧面启用(PULL/BOTH)就提供能力（兼容某些管道/网络）
            if (side == null) {
                boolean anyEnabled = false;
                for (Direction d : Direction.values()) {
                    if (d == Direction.UP) continue;
                    SideMode m = getSideMode(d);
                    if (m == SideMode.PULL || m == SideMode.BOTH) {
                        anyEnabled = true;
                        break;
                    }
                }
                return anyEnabled ? fluidCap.cast() : LazyOptional.empty();
            }

            // side != null：该面不是 OFF 就提供（PULL / BOTH 都算）
            SideMode mode = getSideMode(side);
            if (mode == SideMode.OFF) return LazyOptional.empty();
            return fluidCap.cast();
        }

        // Mekanism 化学品（Gas）
        if (MekanismChecker.isLoaded()) {
            LazyOptional<T> mekCap = getChemicalCapability(cap, side);
            if (mekCap != null) return mekCap;
        }

        return super.getCapability(cap, side);
    }

    /**
     * Mekanism 化学品能力检查（独立方法避免类加载问题）。
     */
    @Nullable
    private <T> LazyOptional<T> getChemicalCapability(Capability<T> cap, @Nullable Direction side) {
        if (!hasValidBinding()) return null;
        if (getCoreBindType() != BindType.CHEMICAL) return null;
        if (side == Direction.UP) return null;

        // side==null 时也允许
        if (side == null) {
            boolean anyEnabled = false;
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                SideMode m = getSideMode(d);
                if (m == SideMode.PULL || m == SideMode.BOTH) { anyEnabled = true; break; }
            }
            if (!anyEnabled) return LazyOptional.empty();
        } else {
            if (getSideMode(side) == SideMode.OFF) return LazyOptional.empty();
        }

        InfiniteCoreItem.MekChemicalKind kind = getBoundChemicalKind();
        switch (kind) {
            case GAS -> {
                if (cap != MekChemicalHelper.GAS_HANDLER_CAP) return null;
                if (cachedGasCap== null || !cachedGasCap.isPresent()) {
                    cachedGasCap = LazyOptional.of(() -> getInfiniteGasOutput());
                }
                return cachedGasCap.cast();
            }
            case INFUSION -> {
                if (cap != MekChemicalHelper.INFUSION_HANDLER_CAP) return null;
                if (cachedInfusionCap == null || !cachedInfusionCap.isPresent()) {
                    cachedInfusionCap = LazyOptional.of(() -> getInfiniteInfusionOutput());
                }
                return cachedInfusionCap.cast();
            }
            case PIGMENT -> {
                if (cap != MekChemicalHelper.PIGMENT_HANDLER_CAP) return null;
                if (cachedPigmentCap== null || !cachedPigmentCap.isPresent()) {
                    cachedPigmentCap = LazyOptional.of(() -> getInfinitePigmentOutput());
                }
                return cachedPigmentCap.cast();
            }
            case SLURRY -> {
                if (cap != MekChemicalHelper.SLURRY_HANDLER_CAP) return null;
                if (cachedSlurryCap== null || !cachedSlurryCap.isPresent()) {
                    cachedSlurryCap = LazyOptional.of(() -> getInfiniteSlurryOutput());
                }
                return cachedSlurryCap.cast();
            }
        }
        return null;
    }



    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
        energyCap.invalidate();
        if (cachedGasCap != null) cachedGasCap.invalidate();
        if (cachedInfusionCap != null) cachedInfusionCap.invalidate();
        if (cachedPigmentCap != null) cachedPigmentCap.invalidate();
        if (cachedSlurryCap != null) cachedSlurryCap.invalidate();
    }

    /** 刷新流体 capability（面模式变更后调用） */
    public void refreshFluidCap() {
        fluidCap.invalidate();
        fluidCap = LazyOptional.of(() -> infiniteOutput);
        // 同时清除化学品缓存
        if (cachedGasCap != null) { cachedGasCap.invalidate(); cachedGasCap = null; }
        if (cachedInfusionCap != null) { cachedInfusionCap.invalidate(); cachedInfusionCap = null; }
        if (cachedPigmentCap != null) { cachedPigmentCap.invalidate(); cachedPigmentCap = null; }
        if (cachedSlurryCap != null) { cachedSlurryCap.invalidate(); cachedSlurryCap = null; }
    }

    /* ====== 构造器 ====== */

    public InfiniteFluidMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFINITE_FLUID_MACHINE.get(), pos, state);
        for (Direction d : Direction.values()) {
            if (d != Direction.UP) sideModes.put(d, SideMode.OFF);
        }
    }

    /* ====== 服务端 Tick ====== */

    public static void serverTick(Level level, BlockPos pos, BlockState state, InfiniteFluidMachineBlockEntity be) {
        be.trySyncEnergyToClient();

        be.pullBudgetRemaining = Modconfigs.BASE_PULL_PER_TICK.get();
        be.chemPullBudgetRemaining = Modconfigs.BASE_PULL_PER_TICK.get();

        if (be.getCoreSlot().getStackInSlot(0).isEmpty()) return;

        // 检测状态变化，通知邻居刷新
        boolean validNow = be.hasValidBinding();
        if (validNow != be.lastTickCanWork) {
            be.lastTickCanWork = validNow;
            be.refreshFluidCap();
            level.updateNeighborsAt(pos, state.getBlock());
        }

        // LIT 发光状态
        boolean hasCore = !be.getCoreSlot().getStackInSlot(0).isEmpty();
        boolean isLit = state.getValue(InfiniteFluidMachineBlock.LIT);
        if (hasCore != isLit) {
            level.setBlock(pos, state.setValue(InfiniteFluidMachineBlock.LIT, hasCore), 3);
        }

        BindType bindType = be.getCoreBindType();
        if (bindType == BindType.NONE) return;

        int cost = be.calcFePerTick();
        if (!be.consumeFe(cost)) return;

        // BOTH 模式主动推送
        int pushAmount = Modconfigs.BASE_PUSH_PER_TICK.get();

        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            if (be.getSideMode(dir) != SideMode.BOTH) continue;
            if (pushAmount <= 0) continue;

            if (bindType == BindType.FLUID) {
                Fluid fluid = be.getBoundSourceFluid();
                if (fluid == null) continue;

                BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
                if (neighbor == null) continue;

                neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite()).ifPresent(handler -> {
                    if (handler.fill(new FluidStack(fluid, 1), IFluidHandler.FluidAction.SIMULATE) > 0) {
                        handler.fill(new FluidStack(fluid, pushAmount), IFluidHandler.FluidAction.EXECUTE);
                    }
                });
            } else if (bindType == BindType.CHEMICAL && MekanismChecker.isLoaded()) {
                ResourceLocation chemId = be.getBoundChemicalId();
                if (chemId != null) {
                    var kind = be.getBoundChemicalKind();
                    MekChemicalHelper.pushAnyChemical(level, pos, dir, kind, chemId, pushAmount);
                }
            }
        }

        be.trySyncEnergyToClient();
    }

    /* ====== 绑定液体解析 ====== */

    @Nullable
    public Fluid getBoundSourceFluid() {
        ItemStack core = coreSlot.getStackInSlot(0);
        if (core.isEmpty()) return null;

        ResourceLocation boundId = InfiniteCoreItem.getBoundFluid(core);
        if (boundId == null) return null;
        if (Modconfigs.isFluidBanned(boundId)) return null;

        Fluid fluid = BuiltInRegistries.FLUID.get(boundId);
        if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
        return fluid;
    }

    @Nullable
    public Component getBoundSubstanceName() {
        BindType type = getCoreBindType();

        if (type == BindType.FLUID) {
            Fluid fluid = getBoundSourceFluid();
            return fluid != null ? fluid.getFluidType().getDescription() : null;
        } else if (type == BindType.CHEMICAL && MekanismChecker.isLoaded()) {
            ResourceLocation chemId = getBoundChemicalId();
            if (chemId == null) return null;
            // ★ 传入 kind，精确查对应注册表
            InfiniteCoreItem.MekChemicalKind kind = getBoundChemicalKind();
            return MekChemicalHelper.getChemicalNameByKind(kind, chemId);
        }

        return null;
    }

    /* ====== NBT ====== */

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy = tag.getInt("Energy");
        if (tag.contains("CoreSlot")) {
            coreSlot.deserializeNBT(tag.getCompound("CoreSlot"));
        }
        sideModes.clear();
        if (tag.contains("SideModes")) {
            CompoundTag modesTag = tag.getCompound("SideModes");
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                String s = modesTag.getString(d.getName());
                if (!s.isEmpty()) {
                    try {
                        sideModes.put(d, SideMode.valueOf(s));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy);
        tag.put("CoreSlot", coreSlot.serializeNBT());
        CompoundTag modes = new CompoundTag();
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            modes.putString(d.getName(), getSideMode(d).name());
        }
        tag.put("SideModes", modes);
    }

    /* ====== 客户端同步 ====== */

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private int energyLastSynced = Integer.MIN_VALUE;
    private int energySyncCooldown = 0;
    private boolean energyDirtyForSync = false;

    public void markEnergyDirtyForSync() { energyDirtyForSync = true; }

    private void trySyncEnergyToClient() {
        if (level == null || level.isClientSide) return;
        if (energySyncCooldown > 0) { energySyncCooldown--; return; }
        if (!energyDirtyForSync && Math.abs(energy - energyLastSynced) < 1) return;

        BlockState st = getBlockState();
        setChanged();
        level.sendBlockUpdated(worldPosition, st, st, 3);
        energyLastSynced = energy;
        energyDirtyForSync = false;
        energySyncCooldown = 5;
    }

    /* ====== 核心变更通知 ====== */

    public void onCoreChanged() {
        if (level == null || level.isClientSide) return;
        setChanged();
        refreshFluidCap();
        BlockState st = getBlockState();
        level.sendBlockUpdated(worldPosition, st, st, 3);
    }

    /* ====== Capability 变更通知 ====== */

    private void notifyCapabilityChanged(Direction side) {
        if (level == null || level.isClientSide) return;
        setChanged();
        refreshFluidCap();

        BlockState st = getBlockState();
        if (st.getBlock() instanceof InfiniteFluidMachineBlock) {
            boolean dirty = st.getValue(InfiniteFluidMachineBlock.DIRTY);
            st = st.setValue(InfiniteFluidMachineBlock.DIRTY, !dirty);
            level.setBlock(worldPosition, st, 3);
        }

        level.updateNeighborsAt(worldPosition, st.getBlock());

        if (side != null && side != Direction.UP) {
            BlockPos npos = worldPosition.relative(side);
            BlockState ns = level.getBlockState(npos);
            level.updateNeighborsAt(npos, ns.getBlock());
            level.sendBlockUpdated(npos, ns, ns, 3);
        }

        level.sendBlockUpdated(worldPosition, st, st, 3);
    }

    //读取MEK的kind
    public InfiniteCoreItem.MekChemicalKind getBoundChemicalKind() {
        ItemStack core = coreSlot.getStackInSlot(0);
        if (core.isEmpty()) return InfiniteCoreItem.MekChemicalKind.GAS;
        return InfiniteCoreItem.getBoundChemicalKind(core);
    }

    private Object gasOut, infusionOut, pigmentOut, slurryOut;

    public Object getInfiniteGasOutput() {
        if (gasOut == null) {
            gasOut = new InfiniteGasOutput(
                    () -> {
                        ResourceLocation id = getBoundChemicalId();
                        return id != null ? MekChemicalHelper.getChemical(id) : null;
                    },
                    this::canWorkNow,
                    () -> chemPullBudgetRemaining,
                    consumed -> chemPullBudgetRemaining -= consumed
            );
        }
        return gasOut;
    }
    public Object getInfiniteInfusionOutput() {
        if (infusionOut == null) {
            infusionOut = new InfiniteInfusionOutput(
                    () -> {
                        ResourceLocation id = getBoundChemicalId();
                        if (id == null) return null;
                        // 绑定的灌注类型
                        return mekanism.api.MekanismAPI.infuseTypeRegistry().getValue(id);
                    },
                    this::canWorkNow,
                    () -> chemPullBudgetRemaining,
                    consumed -> chemPullBudgetRemaining -= consumed
            );
        }
        return infusionOut;
    }
    public Object getInfinitePigmentOutput() {
        if (pigmentOut == null) {
            pigmentOut = new InfinitePigmentOutput(
                    () -> {
                        ResourceLocation id = getBoundChemicalId();
                        if (id == null) return null;
                        // 绑定的颜料类型
                        return mekanism.api.MekanismAPI.pigmentRegistry().getValue(id);
                    },
                    this::canWorkNow,
                    () -> chemPullBudgetRemaining,
                    consumed -> chemPullBudgetRemaining -= consumed
            );
        }
        return pigmentOut;
    }

    public Object getInfiniteSlurryOutput() {
        if (slurryOut == null) {
            slurryOut = new InfiniteSlurryOutput(
                    () -> {
                        ResourceLocation id = getBoundChemicalId();
                        if (id == null) return null;
                        // 绑定的浆液类型
                        return mekanism.api.MekanismAPI.slurryRegistry().getValue(id);
                    },
                    this::canWorkNow,
                    () -> chemPullBudgetRemaining,
                    consumed -> chemPullBudgetRemaining -= consumed
            );
        }
        return slurryOut;
    }



}
