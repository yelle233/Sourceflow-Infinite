package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.DestructionMachineBlock;
import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper;
import com.yelle233.yuanliuwujin.item.DestructionCoreItem;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.EnumMap;

/**
 * 销毁机器方块实体（1.20.1 Forge v2.0版本）
 * <p>
 * 核心特性：虚空储罐、面速率控制、超频压力机制、分级核心系统
 * <p>
 * 面模式：OFF / PUSH（被动接受） / BOTH（被动+主动抽取）
 */
public class DestructionMachineBlockEntity extends BlockEntity implements ICoreMachine {

    public enum SideMode { OFF, PUSH, BOTH }

    private boolean lastTickCanWork = false;
    private float pressure          = 0.0f;
    private int lastTickFEConsumed  = 0;
    private int secondTick          = 0;
    private int chemBudgetRemaining = 0;

    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> faceRates  = new EnumMap<>(Direction.class);

    private boolean hadRedstoneSignal = false;
    private final EnumMap<Direction, SideMode> savedSideModes = new EnumMap<>(Direction.class);

    private final ItemStackHandler coreSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) { setChanged(); onCoreChanged(); }
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.getItem() instanceof DestructionCoreItem;
        }
        @Override public int getSlotLimit(int slot) { return 1; }
    };

    private final MachineEnergyStorage energyStorage = new MachineEnergyStorage(this::setChanged);

    private FluidTank voidTank;

    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.empty();
    private LazyOptional<IFluidHandler> voidTankReadCap = LazyOptional.empty();
    private LazyOptional<IFluidHandler> voidTankCap = LazyOptional.empty();
    private final EnumMap<Direction, LazyOptional<IFluidHandler>> fluidCaps = new EnumMap<>(Direction.class);
    private Object gasSink, infusionSink, pigmentSink, slurrySink;
    private final EnumMap<Direction, LazyOptional<?>> gasCaps      = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, LazyOptional<?>> infusionCaps = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, LazyOptional<?>> pigmentCaps  = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, LazyOptional<?>> slurryCaps   = new EnumMap<>(Direction.class);

    public DestructionMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DESTRUCTION_MACHINE.get(), pos, state);
        rebuildVoidTank();
        initFaceRates();
        rebuildCapabilities();
    }

    private void rebuildVoidTank() {
        int capacity = Modconfigs.MACHINE_VOID_TANK_CAPACITY.get();
        voidTank = new FluidTank(capacity) {
            @Override protected void onContentsChanged() { setChanged(); }
            @Override public boolean isFluidValid(int tank, FluidStack stack) {
                return stack.getFluid().isSame(ModFluids.VOID_FLUID_SOURCE.get());
            }
        };
    }

    private void initFaceRates() {
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            faceRates.put(dir, 1);
        }
    }

    private void rebuildCapabilities() {
        energyCap = LazyOptional.of(() -> energyStorage);
        voidTankReadCap = LazyOptional.of(() -> makeVoidTankReadOnly());
        voidTankCap = LazyOptional.of(() -> voidTank);
        fluidCaps.forEach((d, lo) -> lo.invalidate());
        fluidCaps.clear();
        if (MekanismChecker.isLoaded()) buildMekSinks();
    }

    private void buildMekSinks() {
        gasSink = new com.yelle233.yuanliuwujin.compat.mekanism.DestructionGasSink(
                this::canWorkNow, this::getVoidTank, this::getCurrentRatio, () -> chemBudgetRemaining);
        infusionSink = new com.yelle233.yuanliuwujin.compat.mekanism.DestructionInfusionSink(
                this::canWorkNow, this::getVoidTank, this::getCurrentRatio, () -> chemBudgetRemaining);
        pigmentSink = new com.yelle233.yuanliuwujin.compat.mekanism.DestructionPigmentSink(
                this::canWorkNow, this::getVoidTank, this::getCurrentRatio, () -> chemBudgetRemaining);
        slurrySink = new com.yelle233.yuanliuwujin.compat.mekanism.DestructionSlurrySink(
                this::canWorkNow, this::getVoidTank, this::getCurrentRatio, () -> chemBudgetRemaining);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DestructionMachineBlockEntity be) {
        if (level.isClientSide) return;
        be.serverTick(level, pos, state);
    }

    private void serverTick(Level level, BlockPos pos, BlockState state) {
        secondTick = (secondTick + 1) % 20;

        boolean hasCore      = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotFull  = voidTank.getFluidAmount() < voidTank.getCapacity();
        int baseFE           = Modconfigs.DESTROY_FE_BASE.get();
        int requiredFE       = calcRequiredFE();
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        int availableEnergy  = energyStorage.getEnergyStored();

        boolean canWork      = hasCore && voidNotFull && anyFaceEnabled && availableEnergy >= requiredFE;

        if (canWork) {
            lastTickFEConsumed = requiredFE;
            energyStorage.extractEnergy(requiredFE, false);
        } else if (hasCore) {
            int standby = Math.min(baseFE, availableEnergy);
            lastTickFEConsumed = standby;
            if (standby > 0) energyStorage.extractEnergy(standby, false);
        } else {
            lastTickFEConsumed = 0;
        }

        chemBudgetRemaining = canWork ? calcTotalChemBudget() : 0;

        if (canWork) {
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (getSideMode(dir) != SideMode.BOTH) continue;
                if (voidTank.getFluidAmount() >= voidTank.getCapacity()) break;
                int tickBudget = calcTickBudget(getFaceRate(dir));

                drainFluidFromNeighbor(level, pos, dir, tickBudget);
                if (MekanismChecker.isLoaded() && chemBudgetRemaining > 0) {
                    long consumed = MekChemicalHelper.drainAndDestroyAnyChemical(level, pos, dir, chemBudgetRemaining);
                    if (consumed > 0) {
                        int ratio = getCurrentRatio();
                        int voidProduced = Math.max(1, (int)(consumed / ratio));
                        voidTank.fill(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), voidProduced), IFluidHandler.FluidAction.EXECUTE);
                        chemBudgetRemaining -= (int) consumed;
                    }
                }
            }

            ItemStack coreStack = coreSlot.getStackInSlot(0);
            if (DestructionCoreItem.isOverclocked(coreStack)) {
                pressure = (float) Math.min(100.0, pressure + Modconfigs.OVERCLOCK_PRESSURE_PER_TICK.get());
                if (pressure >= 100.0f) { triggerExplosion(level, pos); return; }
            }
        } else {
            if (pressure > 0) pressure = (float) Math.max(0.0, pressure - Modconfigs.PRESSURE_DECAY_PER_TICK.get());
        }

        if (!voidTank.isEmpty()) {
            pushVoidFluidDown(level, pos);
        }

        boolean currentLit = state.getValue(DestructionMachineBlock.LIT);
        if (hasCore != currentLit) {
            level.setBlock(pos, state.setValue(DestructionMachineBlock.LIT, hasCore), 3);
        }
        lastTickCanWork = canWork;
        lastTickEndEnergy = energyStorage.getEnergyStored();
        setChanged();
        syncToClient();
    }

    private void drainFluidFromNeighbor(Level level, BlockPos pos, Direction dir, int budget) {
        if (voidTank.getFluidAmount() >= voidTank.getCapacity()) return;
        BlockPos neighborPos = pos.relative(dir);
        var beNeighbor = level.getBlockEntity(neighborPos);
        if (beNeighbor == null) return;
        beNeighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite()).ifPresent(handler -> {
            int ratio = getCurrentRatio();
            int spaceInMb = voidTank.getCapacity() - voidTank.getFluidAmount();
            int maxAccept = (int) Math.min((long) spaceInMb * ratio, budget);
            if (maxAccept <= 0) return;
            FluidStack drained = handler.drain(maxAccept, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                int voidProduced = Math.max(1, drained.getAmount() / ratio);
                voidTank.fill(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), voidProduced), IFluidHandler.FluidAction.EXECUTE);
            }
        });
    }

    private void pushVoidFluidDown(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        var beBelow = level.getBlockEntity(below);
        if (beBelow == null) return;
        beBelow.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).ifPresent(handler -> {
            FluidStack toSend = voidTank.getFluid().copy();
            int filled = handler.fill(toSend, IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0) voidTank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        });
    }

    /** 被动接受流体 */
    public int acceptFluidFromSide(FluidStack offered, Direction dir) {
        if (!canWorkNow()) return 0;
        int ratio = getCurrentRatio();
        int spaceInMb = voidTank.getCapacity() - voidTank.getFluidAmount();
        int tickBudget = calcTickBudget(getFaceRate(dir));
        int maxAccept = (int) Math.min(Math.min((long) spaceInMb * ratio, offered.getAmount()), tickBudget);
        if (maxAccept <= 0) return 0;
        int voidProduced = Math.max(1, maxAccept / ratio);
        voidTank.fill(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), voidProduced), IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        return maxAccept;
    }

    private void triggerExplosion(Level level, BlockPos pos) {
        coreSlot.setStackInSlot(0, ItemStack.EMPTY);
        level.removeBlock(pos, false);
        float strength = Modconfigs.EXPLOSION_STRENGTH.get().floatValue();
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                strength, true, Level.ExplosionInteraction.TNT);
        if (level instanceof ServerLevel serverLevel) {
            VoidFluidBlock.placeAt(serverLevel, pos);
        }
        int blockCount = Math.min(Modconfigs.EXPLOSION_VOID_BLOCKS.get(),
                voidTank.getFluidAmount() / 500 + Modconfigs.EXPLOSION_VOID_BLOCKS.get() / 2);
        blockCount = Math.min(blockCount, 256);
        int radius = Math.max(3, (int) Math.sqrt(blockCount));
        RandomSource rand = level.random;
        for (int i = 0; i < blockCount; i++) {
            BlockPos target = pos.offset(
                    rand.nextIntBetweenInclusive(-radius, radius),
                    rand.nextIntBetweenInclusive(-radius / 2, radius),
                    rand.nextIntBetweenInclusive(-radius, radius));
            if (level instanceof ServerLevel serverLevel) {
                VoidFluidBlock.placeAt(serverLevel, target);
            }
        }
    }

    // ── 预算计算 ────────────────────────────────────────────

    private int calcTickBudget(int ratePerSecond) {
        int base = ratePerSecond / 20;
        int remainder = ratePerSecond % 20;
        return (secondTick < remainder) ? base + 1 : base;
    }

    private int calcTotalChemBudget() {
        int total = 0;
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (getSideMode(dir) != SideMode.OFF) {
                total += calcTickBudget(getFaceRate(dir));
            }
        }
        return total;
    }

    public int calcRequiredFE() {
        long fe = Modconfigs.DESTROY_FE_BASE.get();
        int coeff = Modconfigs.DESTROY_FE_PER_MB_RATE.get();
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (getSideMode(dir) != SideMode.OFF)
                fe += (long) Math.max(1, getFaceRate(dir) / 20) * coeff;
        }
        return (int) Math.min(fe, Integer.MAX_VALUE - 1);
    }

    public int getCurrentRatio() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        return Modconfigs.getDestroyRatio(DestructionCoreItem.getLevel(cs), DestructionCoreItem.isOverclocked(cs));
    }

    public boolean canWorkNow() {
        if (level == null || level.isClientSide) return lastTickCanWork;
        boolean hasCore     = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotFull = voidTank.getFluidAmount() < voidTank.getCapacity();
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        boolean hasEnoughFE = energyStorage.getEnergyStored() >= calcRequiredFE();
        return hasCore && voidNotFull && anyFaceEnabled && hasEnoughFE;
    }

    // ── ICoreMachine ────────────────────────────────────────

    @Override public ItemStackHandler getCoreSlot() { return coreSlot; }

    @Override
    public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP || dir == Direction.DOWN) return;
        SideMode old  = getSideMode(dir);
        SideMode next = switch (old) {
            case OFF  -> SideMode.PUSH;
            case PUSH -> SideMode.BOTH;
            case BOTH -> SideMode.OFF;
        };
        sideModes.put(dir, next);
        if ((old == SideMode.OFF) != (next == SideMode.OFF)) {
            notifyCapabilityChanged(dir);
        } else {
            notifyStateOnly();
        }
    }

    @Override
    public void onCoreChanged() {
        if (level == null) return;
        pressure = 0.0f;
        setChanged();
        syncToClient();
        boolean dirty = !getBlockState().getValue(DestructionMachineBlock.DIRTY);
        level.setBlock(worldPosition, getBlockState().setValue(DestructionMachineBlock.DIRTY, dirty), 3);
    }

    @Override public boolean isValidCoreItem(Item item) { return item instanceof DestructionCoreItem; }

    @Override
    public int getFaceRate(Direction dir) {
        if (dir == Direction.UP || dir == Direction.DOWN) return Integer.MAX_VALUE - 1;
        return faceRates.getOrDefault(dir, 20);
    }

    @Override
    public void adjustFaceRate(Direction dir, int delta) {
        if (dir == Direction.UP || dir == Direction.DOWN) return;
        int current = getFaceRate(dir);
        long next   = (long) current + delta;
        faceRates.put(dir, (int) Math.max(1, Math.min(Integer.MAX_VALUE - 1L, next)));
        setChanged();
        syncToClient();
    }

    public SideMode getSideMode(Direction dir) {
        if (dir == Direction.UP) return SideMode.OFF;
        if (dir == Direction.DOWN) return SideMode.PUSH;
        return sideModes.getOrDefault(dir, SideMode.OFF);
    }

    public boolean hasCoreInserted() { return !coreSlot.getStackInSlot(0).isEmpty(); }

    /**
     * 虚空储罐的只读Handler（用于Jade等信息模组查询）
     */
    private IFluidHandler makeVoidTankReadOnly() {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public @NotNull FluidStack getFluidInTank(int tank) {
                return voidTank.getFluid().copy();
            }
            @Override public int getTankCapacity(int tank) {
                return voidTank.getCapacity();
            }
            @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return false; }
            @Override public int fill(@NotNull FluidStack resource, FluidAction action) { return 0; }
            @Override public @NotNull FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
            @Override public @NotNull FluidStack drain(@NotNull FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        };
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY && (side == Direction.UP || side == null)) {
            return energyCap.cast();
        }

        if (cap == ForgeCapabilities.FLUID_HANDLER && side == null) {
            return voidTankReadCap.cast();
        }

        if (cap == ForgeCapabilities.FLUID_HANDLER && side == Direction.DOWN) {
            return voidTankCap.cast();
        }

        if (cap == ForgeCapabilities.FLUID_HANDLER && side != null && side != Direction.UP && side != Direction.DOWN) {
            SideMode mode = getSideMode(side);
            if (mode != SideMode.OFF) {
                return fluidCaps.computeIfAbsent(side, d -> LazyOptional.of(() -> makeSideSinkHandler(d))).cast();
            }
        }

        if (MekanismChecker.isLoaded() && side != null && side != Direction.UP && side != Direction.DOWN) {
            SideMode mode = getSideMode(side);
            if (mode != SideMode.OFF) {
                if (cap == MekChemicalHelper.GAS_HANDLER_CAP && gasSink != null)
                    return gasCaps.computeIfAbsent(side, d -> LazyOptional.of(() -> gasSink)).cast();
                if (cap == MekChemicalHelper.INFUSION_HANDLER_CAP && infusionSink != null)
                    return infusionCaps.computeIfAbsent(side, d -> LazyOptional.of(() -> infusionSink)).cast();
                if (cap == MekChemicalHelper.PIGMENT_HANDLER_CAP && pigmentSink != null)
                    return pigmentCaps.computeIfAbsent(side, d -> LazyOptional.of(() -> pigmentSink)).cast();
                if (cap == MekChemicalHelper.SLURRY_HANDLER_CAP && slurrySink != null)
                    return slurryCaps.computeIfAbsent(side, d -> LazyOptional.of(() -> slurrySink)).cast();
            }
        }

        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        voidTankReadCap.invalidate();
        voidTankCap.invalidate();
        fluidCaps.values().forEach(LazyOptional::invalidate);
        gasCaps.values().forEach(LazyOptional::invalidate);
        infusionCaps.values().forEach(LazyOptional::invalidate);
        pigmentCaps.values().forEach(LazyOptional::invalidate);
        slurryCaps.values().forEach(LazyOptional::invalidate);
    }

    private IFluidHandler makeSideSinkHandler(Direction dir) {
        return new IFluidHandler() {
            @Override public int getTanks() { return canWorkNow() ? 1 : 0; }
            @Override public @NotNull FluidStack getFluidInTank(int t) { return FluidStack.EMPTY; }
            @Override public int getTankCapacity(int t) {
                if (!canWorkNow()) return 0;
                int ratio = getCurrentRatio();
                long space = (long)(voidTank.getCapacity() - voidTank.getFluidAmount()) * ratio;
                return (int) Math.min(space, Integer.MAX_VALUE);
            }
            @Override public boolean isFluidValid(int t, @NotNull FluidStack fs) { return canWorkNow(); }
            @Override
            public int fill(FluidStack resource, @NotNull FluidAction action) {
                if (!canWorkNow() || resource.isEmpty()) return 0;
                int tickBudget = calcTickBudget(getFaceRate(dir));
                int limited    = Math.min(resource.getAmount(), tickBudget);
                if (action.simulate()) {
                    int ratio = getCurrentRatio();
                    int space = voidTank.getCapacity() - voidTank.getFluidAmount();
                    return (int) Math.min(limited, (long) space * ratio);
                }
                FluidStack limited2 = resource.copy();
                limited2.setAmount(limited);
                return acceptFluidFromSide(limited2, dir);
            }
            @Override public @NotNull FluidStack drain(int m, @NotNull FluidAction a) { return FluidStack.EMPTY; }
            @Override public @NotNull FluidStack drain(@NotNull FluidStack r, @NotNull FluidAction a) { return FluidStack.EMPTY; }
        };
    }

    // ── 状态通知 ────────────────────────────────────────────

    private void notifyCapabilityChanged(Direction dir) {
        if (level == null || level.isClientSide) return;
        LazyOptional<IFluidHandler> old = fluidCaps.remove(dir);
        if (old != null) old.invalidate();
        if (MekanismChecker.isLoaded()) {
            LazyOptional<?> g = gasCaps.remove(dir); if (g != null) g.invalidate();
            LazyOptional<?> i = infusionCaps.remove(dir); if (i != null) i.invalidate();
            LazyOptional<?> p = pigmentCaps.remove(dir); if (p != null) p.invalidate();
            LazyOptional<?> s = slurryCaps.remove(dir); if (s != null) s.invalidate();
        }
        setChanged();
        BlockState st = getBlockState();
        if (st.getBlock() instanceof DestructionMachineBlock) {
            boolean dirty = st.getValue(DestructionMachineBlock.DIRTY);
            level.setBlock(worldPosition, st.setValue(DestructionMachineBlock.DIRTY, !dirty), 3);
        }
        level.updateNeighborsAt(worldPosition, st.getBlock());
        level.sendBlockUpdated(worldPosition, st, st, 3);
    }

    private void notifyStateOnly() {
        if (level == null || level.isClientSide) return;
        setChanged();
        BlockState st = getBlockState();
        if (st.getBlock() instanceof DestructionMachineBlock) {
            boolean dirty = st.getValue(DestructionMachineBlock.DIRTY);
            level.setBlock(worldPosition, st.setValue(DestructionMachineBlock.DIRTY, !dirty), 3);
        }
        level.sendBlockUpdated(worldPosition, st, st, 3);
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ── Getter ──────────────────────────────────────────────
    public FluidTank getVoidTank()           { return voidTank; }
    public IEnergyStorage getEnergyStorage()  { return energyStorage; }
    public float getPressure()                { return pressure; }
    public int getLastTickFEConsumed()        { return lastTickFEConsumed; }
    public int getChemBudgetRemaining()       { return chemBudgetRemaining; }

    // ── 红石控制 ──────────────────────────────────────────────
    public boolean hadRedstoneSignal() { return hadRedstoneSignal; }
    public void setRedstoneSignal(boolean signal) { hadRedstoneSignal = signal; }

    /**
     * 切换红石控制：保存当前状态并关闭所有侧面，或恢复保存的状态
     */
    public void toggleRedstoneControl() {
        boolean allOff = true;
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (getSideMode(dir) != SideMode.OFF) {
                allOff = false;
                break;
            }
        }

        if (allOff) {
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                SideMode saved = savedSideModes.get(dir);
                if (saved != null && saved != SideMode.OFF) {
                    sideModes.put(dir, saved);
                }
            }
            savedSideModes.clear();
        } else {
            savedSideModes.clear();
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                SideMode current = getSideMode(dir);
                if (current != SideMode.OFF) {
                    savedSideModes.put(dir, current);
                    sideModes.put(dir, SideMode.OFF);
                }
            }
        }

        notifyCapabilityChanged(null);
        setChanged();
        syncToClient();
    }

    // ── NBT ─────────────────────────────────────────────────

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("core")) coreSlot.deserializeNBT(tag.getCompound("core"));
        energyStorage.setEnergy(tag.getInt("energy"));
        rebuildVoidTank();
        if (tag.contains("voidTank")) voidTank.readFromNBT(tag.getCompound("voidTank"));
        pressure      = tag.getFloat("pressure");
        lastTickCanWork = tag.getBoolean("lastCanWork");
        lastTickFEConsumed = tag.getInt("lastFE");

        CompoundTag modesTag = tag.getCompound("sideModes");
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            String s = modesTag.getString(dir.getName());
            if (!s.isEmpty()) { try { sideModes.put(dir, SideMode.valueOf(s)); } catch (IllegalArgumentException ignored) {} }
        }
        CompoundTag ratesTag = tag.getCompound("faceRates");
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            if (ratesTag.contains(dir.getName())) faceRates.put(dir, Math.max(1, ratesTag.getInt(dir.getName())));
        }

        hadRedstoneSignal = tag.getBoolean("hadRedstoneSignal");
        CompoundTag savedModesTag = tag.getCompound("savedSideModes");
        savedSideModes.clear();
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            String modeStr = savedModesTag.getString(dir.getName());
            if (!modeStr.isEmpty()) {
                try {
                    savedSideModes.put(dir, SideMode.valueOf(modeStr));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("core", coreSlot.serializeNBT());
        tag.putInt("energy", energyStorage.getEnergyStored());
        tag.put("voidTank", voidTank.writeToNBT(new CompoundTag()));
        tag.putFloat("pressure", pressure);
        tag.putBoolean("lastCanWork", lastTickCanWork);
        tag.putInt("lastFE", lastTickFEConsumed);
        CompoundTag modesTag = new CompoundTag();
        sideModes.forEach((d, m) -> modesTag.putString(d.getName(), m.name()));
        tag.put("sideModes", modesTag);
        CompoundTag ratesTag = new CompoundTag();
        faceRates.forEach((d, r) -> ratesTag.putInt(d.getName(), r));
        tag.put("faceRates", ratesTag);

        tag.putBoolean("hadRedstoneSignal", hadRedstoneSignal);
        CompoundTag savedModesTag = new CompoundTag();
        savedSideModes.forEach((dir, mode) -> savedModesTag.putString(dir.getName(), mode.name()));
        tag.put("savedSideModes", savedModesTag);
    }

    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

