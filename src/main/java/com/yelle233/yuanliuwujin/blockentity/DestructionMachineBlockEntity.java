package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.DestructionMachineBlock;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.*;
import com.yelle233.yuanliuwujin.item.DestructionCoreItem;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
 * 销毁机器的方块实体（1.20.1 Forge 版本）。
 * <p>
 * 面模式与无限流体机器完全对称：
 * <ul>
 *   <li>无限机器：PULL = 允许外部抽取 / BOTH = 允许抽取 + 机器主动推送</li>
 *   <li>销毁机器：PUSH = 允许外部推入 / BOTH = 允许推入 + 机器主动抽取</li>
 * </ul>
 * 即 BOTH 在两台机器中都代表"机器主动出力"，PULL/PUSH 代表"只开放被动接口"。
 * <p>
 * 1.20.1 Forge 关键差异：
 * <ul>
 *   <li>使用 {@link LazyOptional} + {@code getCapability()} 暴露能力</li>
 *   <li>Mekanism 化学品分为四种独立类型，需要四个 Sink Handler</li>
 *   <li>NBT 序列化使用 {@code load()} / {@code saveAdditional()} 无 registries 参数</li>
 * </ul>
 */
public class DestructionMachineBlockEntity extends BlockEntity implements ICoreMachine {

    private boolean lastTickCanWork = false;
    private int destroyBudgetRemaining = 0;
    private long chemDestroyBudgetRemaining = 0;

    /* ====== 面模式枚举 ====== */

    public enum SideMode {
        /**
         * 面关闭，不暴露任何 capability。
         */
        OFF,

        /**
         * 被动接受（Push-in Only）：此面暴露虚空 FluidHandler/ChemHandler，
         * 外部管道可主动向此面推送流体/化学品，机器将其销毁。
         * 机器自身不会主动抽取。
         */
        PUSH,

        /**
         * 主动抽取（Both Active + Passive）：在 PUSH 的基础上，
         * 机器每 tick 还会主动从相邻容器抽取流体/化学品并销毁。
         */
        BOTH
    }

    /* ====== 面模式管理 ====== */

    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);

    public SideMode getSideMode(Direction dir) {
        return dir == Direction.UP ? SideMode.OFF
                : sideModes.getOrDefault(dir, SideMode.OFF);
    }

    @Override
    public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP) return;
        SideMode old  = getSideMode(dir);
        SideMode next = switch (old) {
            case OFF  -> SideMode.PUSH;
            case PUSH -> SideMode.BOTH;
            case BOTH -> SideMode.OFF;
        };
        sideModes.put(dir, next);

        boolean wasEnabled = (old != SideMode.OFF);
        boolean nowEnabled = (next != SideMode.OFF);

        if (wasEnabled != nowEnabled) {
            notifyCapabilityChanged(dir);
        } else {
            notifyStateOnly();
        }
    }

    private void notifyStateOnly() {
        if (level == null || level.isClientSide) return;
        setChanged();
        BlockState st = getBlockState();
        if (st.getBlock() instanceof DestructionMachineBlock) {
            boolean dirty = st.getValue(DestructionMachineBlock.DIRTY);
            st = st.setValue(DestructionMachineBlock.DIRTY, !dirty);
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
            return stack.getItem() instanceof DestructionCoreItem;
        }
    };

    @Override
    public ItemStackHandler getCoreSlot() { return coreSlot; }

    @Override
    public boolean isValidCoreItem(Item item) { return item instanceof DestructionCoreItem; }

    public boolean hasCoreInserted() { return !coreSlot.getStackInSlot(0).isEmpty(); }

    /* ====== 虚空流体 Handler ====== */

    private final IFluidHandler voidSink = new IFluidHandler() {
        @Override public int getTanks() { return canWorkNow() ? 1 : 0; }
        @Override public @NotNull FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return canWorkNow(); }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!canWorkNow() || resource.isEmpty()) return 0;
            int amt = Math.min(resource.getAmount(), destroyBudgetRemaining);
            if (amt <= 0) return 0;
            if (action.execute()) destroyBudgetRemaining -= amt;
            return amt;
        }

        @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public @NotNull FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    };

    /* ====== Mekanism 化学品虚空 Sink（四种类型各一个）====== */

    private Object gasSink, infusionSink, pigmentSink, slurrySink;

    private Object getGasSink() {
        if (gasSink == null) gasSink = new DestructionGasSink(
                this::canWorkNow,
                () -> chemDestroyBudgetRemaining,
                consumed -> chemDestroyBudgetRemaining -= consumed);
        return gasSink;
    }

    private Object getInfusionSink() {
        if (infusionSink == null) infusionSink = new DestructionInfusionSink(
                this::canWorkNow,
                () -> chemDestroyBudgetRemaining,
                consumed -> chemDestroyBudgetRemaining -= consumed);
        return infusionSink;
    }

    private Object getPigmentSink() {
        if (pigmentSink == null) pigmentSink = new DestructionPigmentSink(
                this::canWorkNow,
                () -> chemDestroyBudgetRemaining,
                consumed -> chemDestroyBudgetRemaining -= consumed);
        return pigmentSink;
    }

    private Object getSlurrySink() {
        if (slurrySink == null) slurrySink = new DestructionSlurrySink(
                this::canWorkNow,
                () -> chemDestroyBudgetRemaining,
                consumed -> chemDestroyBudgetRemaining -= consumed);
        return slurrySink;
    }

    /* ====== Forge Capability LazyOptionals ====== */

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            int received = Math.min(ENERGY_CAPACITY - energy, maxReceive);
            if (!simulate && received > 0) { energy += received; markEnergyDirtyForSync(); setChanged(); }
            return received;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return ENERGY_CAPACITY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };


    private LazyOptional<IFluidHandler> fluidCap = LazyOptional.empty();
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyStorage);

    // Mekanism 化学品 LazyOptionals（按面分配同一个对象）
    private LazyOptional<?> gasCap, infusionCap, pigmentCap, slurryCap;

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // 能量：仅顶面或 null 查询
        if (cap == ForgeCapabilities.ENERGY) {
            if (side == null || side == Direction.UP) return energyCap.cast();
            return LazyOptional.empty();
        }

        // 流体虚空 Sink：null 查询直接返回空，屏蔽 Jade 等工具的探测
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (side == null) return LazyOptional.empty();
            if (side == Direction.UP) return LazyOptional.empty();
            if (getSideMode(side) == SideMode.OFF) return LazyOptional.empty();
            return getFluidCap().cast();
        }

        // Mekanism 化学品
        if (MekanismChecker.isLoaded()) {
            LazyOptional<T> mekCap = getMekCapability(cap, side);
            if (mekCap != null) return mekCap;
        }

        return super.getCapability(cap, side);
    }


    private LazyOptional<IFluidHandler> getFluidCap() {
        if (!fluidCap.isPresent()) fluidCap = LazyOptional.of(() -> voidSink);
        return fluidCap;
    }

    @Nullable
    private <T> LazyOptional<T> getMekCapability(Capability<T> cap, @Nullable Direction side) {

        // 检查面是否启用
        if (side == Direction.UP) return LazyOptional.empty();
        if (getSideMode(side) == SideMode.OFF) return LazyOptional.empty();
        if (side == null) {
            boolean anyEnabled = false;
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                if (getSideMode(d) != SideMode.OFF) { anyEnabled = true; break; }
            }
            if (!anyEnabled) return LazyOptional.empty();
        }

        if (cap == MekChemicalHelper.GAS_HANDLER_CAP) {
            if (gasCap == null || !gasCap.isPresent())
                gasCap = LazyOptional.of(this::getGasSink);
            return gasCap.cast();
        }
        if (cap == MekChemicalHelper.INFUSION_HANDLER_CAP) {
            if (infusionCap == null || !infusionCap.isPresent())
                infusionCap = LazyOptional.of(this::getInfusionSink);
            return infusionCap.cast();
        }
        if (cap == MekChemicalHelper.PIGMENT_HANDLER_CAP) {
            if (pigmentCap == null || !pigmentCap.isPresent())
                pigmentCap = LazyOptional.of(this::getPigmentSink);
            return pigmentCap.cast();
        }
        if (cap == MekChemicalHelper.SLURRY_HANDLER_CAP) {
            if (slurryCap == null || !slurryCap.isPresent())
                slurryCap = LazyOptional.of(this::getSlurrySink);
            return slurryCap.cast();
        }

        return null;
    }

    /** 刷新所有 capability LazyOptional（面模式变更后调用） */
    public void refreshCaps() {
        fluidCap.invalidate();
        fluidCap = LazyOptional.empty();
        if (gasCap != null) { gasCap.invalidate(); gasCap = null; }
        if (infusionCap != null) { infusionCap.invalidate(); infusionCap = null; }
        if (pigmentCap != null) { pigmentCap.invalidate(); pigmentCap = null; }
        if (slurryCap != null) { slurryCap.invalidate(); slurryCap = null; }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
        energyCap.invalidate();
        if (gasCap != null) gasCap.invalidate();
        if (infusionCap != null) infusionCap.invalidate();
        if (pigmentCap != null) pigmentCap.invalidate();
        if (slurryCap != null) slurryCap.invalidate();
    }

    /* ====== 能量系统 ====== */

    private static final int ENERGY_CAPACITY = 1_000_000_000;
    private int energy = 0;



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
        if (!hasCoreInserted()) return 0;
        return Modconfigs.DESTROY_FE_PER_TICK.get()
                + countEnabledFaces() * Modconfigs.DESTROY_FE_PER_ENABLED_FACE.get();
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
            if (m == SideMode.PUSH || m == SideMode.BOTH) count++;
        }
        return count;
    }

    /* ====== 构造器 ====== */

    public DestructionMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DESTRUCTION_MACHINE.get(), pos, state);
        for (Direction d : Direction.values()) {
            if (d != Direction.UP) sideModes.put(d, SideMode.OFF);
        }
    }

    /* ====== 服务端 Tick ====== */

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                   DestructionMachineBlockEntity be) {
        be.trySyncEnergyToClient();

        be.destroyBudgetRemaining     = Modconfigs.DESTROY_PER_TICK.get();
        be.chemDestroyBudgetRemaining = Modconfigs.DESTROY_PER_TICK.get();

        boolean hasCore = be.hasCoreInserted();
        boolean isLit   = state.getValue(DestructionMachineBlock.LIT);
        if (hasCore != isLit) {
            level.setBlock(pos, state.setValue(DestructionMachineBlock.LIT, hasCore), 3);
        }

        if (!hasCore) return;

        boolean validNow = hasCore;
        if (validNow != be.lastTickCanWork) {
            be.lastTickCanWork = validNow;
            be.refreshCaps();
            level.updateNeighborsAt(pos, state.getBlock());
        }

        int cost = be.calcFePerTick();
        if (!be.consumeFe(cost)) return;

        // BOTH 模式：主动从相邻容器抽取流体/化学品并销毁
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            if (be.getSideMode(dir) != SideMode.BOTH) continue;

            // 主动抽取流体
            if (be.destroyBudgetRemaining > 0) {
                BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
                if (neighbor != null) {
                    neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite())
                            .ifPresent(handler -> {
                                FluidStack sim = handler.drain(be.destroyBudgetRemaining,
                                        IFluidHandler.FluidAction.SIMULATE);
                                if (!sim.isEmpty()) {
                                    handler.drain(sim.getAmount(), IFluidHandler.FluidAction.EXECUTE);
                                    be.destroyBudgetRemaining -= sim.getAmount();
                                }
                            });
                }
            }

            // 主动抽取 Mekanism 化学品
            if (MekanismChecker.isLoaded() && be.chemDestroyBudgetRemaining > 0) {
                long consumed = MekChemicalHelper.drainAndDestroyAnyChemical(
                        level, pos, dir, be.chemDestroyBudgetRemaining);
                be.chemDestroyBudgetRemaining -= consumed;
            }
        }

        be.trySyncEnergyToClient();
    }

    /* ====== NBT（1.20.1 风格：无 registries 参数） ====== */

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy = tag.getInt("Energy");
        if (tag.contains("CoreSlot"))
            coreSlot.deserializeNBT(tag.getCompound("CoreSlot"));
        sideModes.clear();
        if (tag.contains("SideModes")) {
            CompoundTag modesTag = tag.getCompound("SideModes");
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                String s = modesTag.getString(d.getName());
                if (!s.isEmpty()) {
                    try { sideModes.put(d, SideMode.valueOf(s)); }
                    catch (IllegalArgumentException ignored) {}
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
        energyLastSynced   = energy;
        energyDirtyForSync = false;
        energySyncCooldown = 5;
    }

    @Override
    public void onCoreChanged() {
        if (level == null || level.isClientSide) return;
        setChanged();
        refreshCaps();
        BlockState st = getBlockState();
        level.sendBlockUpdated(worldPosition, st, st, 3);
    }

    private void notifyCapabilityChanged(Direction side) {
        if (level == null || level.isClientSide) return;
        setChanged();
        refreshCaps();
        BlockState st = getBlockState();
        if (st.getBlock() instanceof DestructionMachineBlock) {
            boolean dirty = st.getValue(DestructionMachineBlock.DIRTY);
            st = st.setValue(DestructionMachineBlock.DIRTY, !dirty);
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

    @Nullable
    public Component getStatusComponent() {
        if (!hasCoreInserted() || !canWorkNow()) return null;
        return Component.translatable("hud.yuanliuwujin.destruction.active");
    }
}
