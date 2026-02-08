package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.InfiniteFluidMachineBlock;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.EnumMap;

public class InfiniteFluidMachineBlockEntity extends BlockEntity {
    private static final SideMode DEFAULT_MODE = SideMode.OFF;
    private static final int DEFAULT_PUSH_PER_TICK = Modconfigs.BASE_PUSH_PER_TICK.get(); // mB/t

    // 六个面的模式：OFF，PULL，BOTH
    public enum SideMode { OFF, PULL, BOTH }

    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> sidePushPerTick = new EnumMap<>(Direction.class);

    public SideMode getSideMode(Direction dir) {
        if (dir == Direction.UP) return SideMode.OFF; // 顶面不给出液
        return sideModes.getOrDefault(dir, DEFAULT_MODE);
    }

    public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP) return;
        SideMode cur = getSideMode(dir);
        SideMode next = switch (cur) {
            case OFF -> SideMode.PULL;
            case PULL -> SideMode.BOTH;
            case BOTH -> SideMode.OFF;
        };
        sideModes.put(dir, next);
        notifyCapabilityChanged(dir);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // 每面输出量（mB/t）
    public int getSidePushPerTick(Direction dir) {
        if (dir == Direction.UP) return 0;
        int v = sidePushPerTick.getOrDefault(dir, DEFAULT_PUSH_PER_TICK);
        if (v < 0) v = 0;
        return v;
    }

    // 1格核心槽
    private final ItemStackHandler coreSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            onCoreChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof InfiniteCoreItem;
        }
    };

    // 每 tick 主动输出多少 mB
    private int pushPerTick = DEFAULT_PUSH_PER_TICK;

    // 对外无限输出
    private final IFluidHandler infiniteOutput = new IFluidHandler() {
        @Override
        public int getTanks() {
            if (!canWorkNow()) return 0;
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (!canWorkNow()) return FluidStack.EMPTY;
            Fluid f = getBoundSourceFluid();
            return f == null ? FluidStack.EMPTY : new FluidStack(f, Integer.MAX_VALUE);
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!canWorkNow()) return FluidStack.EMPTY;
            Fluid f = getBoundSourceFluid();
            if (f == null) return FluidStack.EMPTY;

            if (resource.isEmpty() || resource.getFluid() != f) return FluidStack.EMPTY;
            int amt = resource.getAmount();
            if (amt <= 0) return FluidStack.EMPTY;

            return new FluidStack(f, amt);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            // ✅ 关键修复：没电 / 不可工作 时，PULL 不能抽
            if (!canWorkNow()) return FluidStack.EMPTY;

            Fluid f = getBoundSourceFluid();
            if (f == null) return FluidStack.EMPTY;

            if (maxDrain <= 0) return FluidStack.EMPTY;
            return new FluidStack(f, maxDrain);
        }
    };

    // 构造器：初始化每面模式和输出量
    public InfiniteFluidMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFINITE_FLUID_MACHINE.get(), pos, state);
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            sideModes.put(d, DEFAULT_MODE);
            sidePushPerTick.put(d, DEFAULT_PUSH_PER_TICK);
        }
    }


    public static void serverTick(Level level, BlockPos pos, BlockState state, InfiniteFluidMachineBlockEntity be) {
        be.trySyncEnergyToClient();

        // 必须有核心
        if (be.getCoreSlot().getStackInSlot(0).isEmpty()) return;

        // 必须能解析出绑定液体（源液体）且不在 banlist
        Fluid f = be.getBoundSourceFluid();
        if (f == null) return;

        // ===== 1) 计算本 tick 需要的 FE：全 OFF 低耗电 =====
        final int IDLE_FE_PER_TICK = Modconfigs.FE_PER_TICK.get();
        final int FE_PER_ENABLED_FACE = Modconfigs.FE_PER_ENABLED_FACE_PER_TICK.get();
        int enabledFaces = 0;

        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            SideMode m = be.getSideMode(d);
            if (m == SideMode.PULL || m == SideMode.BOTH) enabledFaces++;
        }

        int cost = IDLE_FE_PER_TICK + enabledFaces * FE_PER_ENABLED_FACE;

        // ===== 2) 没电不工作=====
        if (!be.consumeFe(cost)) {
            return;
        }

        // ===== 3) 有电：只对 BOTH 面主动推送 =====
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            if (be.getSideMode(dir) != SideMode.BOTH) continue;

            int amount = be.getSidePushPerTick(dir);
            if (amount <= 0) continue;

            BlockPos neighborPos = pos.relative(dir);

            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, dir.getOpposite());
            if (handler == null) continue;

            FluidStack test = new FluidStack(f, 1);
            if (handler.fill(test, IFluidHandler.FluidAction.SIMULATE) <= 0) continue;

            handler.fill(new FluidStack(f, amount), IFluidHandler.FluidAction.EXECUTE);
        }

        be.trySyncEnergyToClient();
    }

    public boolean consumeFe(int cost) {
        if (cost <= 0) return true;
        if (energy < cost) return false;
        energy -= cost;
        markEnergyDirtyForSync();
        setChanged();
        return true;
    }

    // 从核心里读取绑定的液体，并强制转换为源液体
    // 强制禁用输出：如果配置 ban 了这个流体，直接返回 null
    @Nullable
    public Fluid getBoundSourceFluid() {
        ItemStack core = coreSlot.getStackInSlot(0);
        if (core.isEmpty()) return null;

        ResourceLocation boundId = InfiniteCoreItem.getBoundFluid(core);
        if (boundId == null) return null;

        // ✅ 机器侧也检查 banlist，防止通过 NBT 绕过绑定限制
        if (Modconfigs.isFluidBanned(boundId)) return null;

        Fluid fluid = BuiltInRegistries.FLUID.get(boundId);
        if (fluid instanceof FlowingFluid ff) {
            fluid = ff.getSource();
        }
        return fluid;
    }

    // ====== 存档 ======

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        energy = tag.getInt("Energy");
        if (tag.contains("CoreSlot")) {
            coreSlot.deserializeNBT(registries, tag.getCompound("CoreSlot"));
        }
        if (tag.contains("PushPerTick")) {
            pushPerTick = tag.getInt("PushPerTick");
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

        sidePushPerTick.clear();
        if (tag.contains("SidePushPerTick")) {
            CompoundTag pushTag = tag.getCompound("SidePushPerTick");
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                if (pushTag.contains(d.getName())) {
                    sidePushPerTick.put(d, pushTag.getInt(d.getName()));
                }
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.put("CoreSlot", coreSlot.serializeNBT(registries));
        tag.putInt("PushPerTick", pushPerTick);
        tag.putInt("Energy", energy);

        CompoundTag modes = new CompoundTag();
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            modes.putString(d.getName(), getSideMode(d).name());
        }
        tag.put("SideModes", modes);

        CompoundTag pushTag = new CompoundTag();
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            pushTag.putInt(d.getName(), getSidePushPerTick(d));
        }
        tag.put("SidePushPerTick", pushTag);
    }

    // ====== getter ======

    public ItemStackHandler getCoreSlot() { return coreSlot; }
    public IFluidHandler getInfiniteOutput() { return infiniteOutput; }
    public int getPushPerTick() { return pushPerTick; }
    public void setPushPerTick(int v) { pushPerTick = Math.max(0, v); setChanged(); }

    // 解决 OFF->PULL 需要重放才生效的问题
    private void notifyCapabilityChanged(Direction side) {
        if (level == null || level.isClientSide) return;

        setChanged();
        BlockState st = getBlockState();

        level.invalidateCapabilities(worldPosition);

        if (st.getBlock() instanceof InfiniteFluidMachineBlock) {
            boolean dirty = st.getValue(InfiniteFluidMachineBlock.DIRTY);
            BlockState newState = st.setValue(InfiniteFluidMachineBlock.DIRTY, !dirty);
            level.setBlock(worldPosition, newState, 3);
            st = newState;
        }

        level.updateNeighborsAt(worldPosition, st.getBlock());

        if (side != null && side != Direction.UP) {
            BlockPos npos = worldPosition.relative(side);
            level.invalidateCapabilities(npos);
            BlockState ns = level.getBlockState(npos);
            level.updateNeighborsAt(npos, ns.getBlock());
            level.sendBlockUpdated(npos, ns, ns, 3);
        }

        level.sendBlockUpdated(worldPosition, st, st, 3);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    // ===== Energy (FE) =====
    private int energy = 0;

    private static final int ENERGY_CAPACITY = Integer.MAX_VALUE;
    private static final int MAX_RECEIVE_PER_TICK = Integer.MAX_VALUE;

    private static final int IDLE_FE_PER_TICK = Modconfigs.FE_PER_TICK.get();
    private static final int FE_PER_ENABLED_FACE_PER_TICK = Modconfigs.FE_PER_ENABLED_FACE_PER_TICK.get();

    private final net.neoforged.neoforge.energy.IEnergyStorage energyStorage =
            new net.neoforged.neoforge.energy.IEnergyStorage() {
                @Override public int receiveEnergy(int maxReceive, boolean simulate) {
                    if (maxReceive <= 0) return 0;
                    int space = ENERGY_CAPACITY - energy;
                    int received = Math.min(space, Math.min(MAX_RECEIVE_PER_TICK, maxReceive));
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

    public net.neoforged.neoforge.energy.IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public int getFeCostPerTick() { return calcFePerTick(); }

    public int countEnabledFaces() {
        int c = 0;
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            SideMode m = getSideMode(d);
            if (m == SideMode.PULL || m == SideMode.BOTH) c++;
        }
        return c;
    }

    public int calcFePerTick() {
        if (coreSlot.getStackInSlot(0).isEmpty()) return 0;

        // ✅ 若 banlist 禁用，则 getBoundSourceFluid() 直接 null → cost=0 → canWorkNow=false
        Fluid bound = getBoundSourceFluid();
        if (bound == null) return 0;

        int enabled = countEnabledFaces();
        return IDLE_FE_PER_TICK + enabled * FE_PER_ENABLED_FACE_PER_TICK;
    }

    public boolean canWorkNow() {
        int cost = calcFePerTick();
        return cost > 0 && energy >= cost;
    }

    // 插入核心后更新方块
    public void onCoreChanged() {
        if (level == null || level.isClientSide) return;

        setChanged();
        BlockState st = getBlockState();
        level.sendBlockUpdated(worldPosition, st, st, 3);
        level.invalidateCapabilities(worldPosition);
    }

    // ===== client sync (energy) =====
    private int energyLastSynced = Integer.MIN_VALUE;
    private int energySyncCooldown = 0;
    private boolean energyDirtyForSync = false;

    public void markEnergyDirtyForSync() {
        energyDirtyForSync = true;
    }

    private void trySyncEnergyToClient() {
        if (level == null || level.isClientSide) return;

        if (energySyncCooldown > 0) {
            energySyncCooldown--;
            return;
        }

        final int THRESHOLD = 1;

        if (!energyDirtyForSync && Math.abs(energy - energyLastSynced) < THRESHOLD) {
            return;
        }

        BlockState st = getBlockState();
        setChanged();
        level.sendBlockUpdated(worldPosition, st, st, 3);
        energyLastSynced = energy;
        energyDirtyForSync = false;
        energySyncCooldown = 5;
    }
}
