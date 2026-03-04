package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.InfiniteFluidMachineBlock;
import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.BindType;
import com.yelle233.yuanliuwujin.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.EnumMap;

public class InfiniteFluidMachineBlockEntity extends BlockEntity implements ICoreMachine {

    public enum SideMode { OFF, PULL, BOTH }

    private boolean lastTickCanWork = false;
    private float pressure = 0.0f;
    private int lastTickFEConsumed = 0;
    private int secondTick = 0;

    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> faceRates = new EnumMap<>(Direction.class);

    // 红石控制相关
    private boolean hadRedstoneSignal = false;
    private final EnumMap<Direction, SideMode> savedSideModes = new EnumMap<>(Direction.class);

    private final ItemStackHandler coreSlot = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { setChanged(); onCoreChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof InfiniteCoreItem && InfiniteCoreItem.hasValidBinding(stack);
        }
        @Override public int getSlotLimit(int slot) { return 1; }
    };

    private final EnergyStorage energyStorage = new MachineEnergyStorage(this::setChanged);

    private FluidTank voidTank;
    private Object chemOutput;
    private int fluidBudgetRemaining = 0;

    public InfiniteFluidMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFINITE_FLUID_MACHINE.get(), pos, state);
        rebuildVoidTank();
        initFaceRates();
        if (MekanismChecker.isLoaded()) {
            chemOutput = com.yelle233.yuanliuwujin.compat.mekanism.MekCompatBridge.createInfiniteChemicalOutput(
                    this::getBoundChemical,
                    this::canWork,
                    this::getVoidTank,
                    this::getCurrentRatio,
                    this::getFluidBudgetRemaining,
                    this::consumeBudget
            );
        }
    }

    private void consumeBudget(int amount) {
        fluidBudgetRemaining = Math.max(0, fluidBudgetRemaining - amount);
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
            faceRates.put(dir, 20);
        }
    }

    private int calcTickBudget(int ratePerSecond) {
        int base = ratePerSecond / 20;
        int remainder = ratePerSecond % 20;
        return (secondTick < remainder) ? base + 1 : base;
    }

    // ── Tick ──
    public static void tick(Level level, BlockPos pos, BlockState state, InfiniteFluidMachineBlockEntity be) {
        if (level.isClientSide) return;
        be.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        secondTick = (secondTick + 1) % 20;

        if (voidTank.getFluidAmount() < voidTank.getCapacity()) {
            pullVoidFluidFromBelow(level, pos);
        }

        boolean hasCore = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotEmpty = !voidTank.isEmpty() || hasVoidFluidInputBelow(level, pos);
        int baseFE = Modconfigs.INFINITE_FE_BASE.get();
        int requiredFE = calcRequiredFE();
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        int availableEnergy = energyStorage.getEnergyStored();

        // 计算能量比例（0.0-1.0）
        double energyRatio = availableEnergy >= requiredFE ? 1.0 : (double) availableEnergy / requiredFE;
        boolean canWork = hasCore && voidNotEmpty && anyFaceEnabled && hasValidBinding() && availableEnergy > baseFE;

        // 按比例消耗电量和工作
        if (canWork) {
            int actualFE = (int) Math.min(availableEnergy, baseFE + (requiredFE - baseFE) * energyRatio);
            lastTickFEConsumed = actualFE;
            energyStorage.extractEnergy(actualFE, false);
        } else if (hasCore) {
            int standbyConsume = Math.min(baseFE, availableEnergy);
            lastTickFEConsumed = standbyConsume;
            if (standbyConsume > 0) energyStorage.extractEnergy(standbyConsume, false);
        } else {
            lastTickFEConsumed = 0;
        }

        fluidBudgetRemaining = canWork ? (int) (calcTotalOutputBudgetThisTick() * energyRatio) : 0;

        if (canWork) {
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (getSideMode(dir) != SideMode.BOTH) continue;
                if (voidTank.isEmpty() || fluidBudgetRemaining <= 0) break;

                BindType bindType = getCoreBindType();
                if (bindType == BindType.FLUID) {
                    pushFluidToNeighbor(level, pos, dir);
                } else if (bindType == BindType.CHEMICAL && MekanismChecker.isLoaded()) {
                    pushChemicalToNeighbor(level, pos, dir);
                }
            }

            ItemStack coreStack = coreSlot.getStackInSlot(0);
            if (InfiniteCoreItem.isOverclocked(coreStack)) {
                pressure = (float) Math.min(100.0, pressure + Modconfigs.OVERCLOCK_PRESSURE_PER_TICK.get());
                if (pressure >= 100.0f) { triggerExplosion(level, pos); return; }
            }
        } else {
            if (pressure > 0) pressure = (float) Math.max(0.0, pressure - Modconfigs.PRESSURE_DECAY_PER_TICK.get());
        }

        BlockState currentState = level.getBlockState(pos);
        boolean currentLit = currentState.getValue(InfiniteFluidMachineBlock.LIT);
        if (hasCore != currentLit) {
            level.setBlock(pos, currentState.setValue(InfiniteFluidMachineBlock.LIT, hasCore), 3);
        }
        lastTickCanWork = canWork;
        setChanged(); syncToClient();
    }

    /** BOTH模式主动推送化学品到邻居 */
    private void pushChemicalToNeighbor(ServerLevel level, BlockPos pos, Direction dir) {
        if (!MekanismChecker.isLoaded()) return;
        ItemStack cs = coreSlot.getStackInSlot(0);
        ResourceLocation chemId = InfiniteCoreItem.getBoundChemical(cs);
        if (chemId == null) return;
        int tickBudget = calcTickBudget(getFaceRate(dir));
        int toBudget = Math.min(tickBudget, fluidBudgetRemaining);
        if (toBudget <= 0) return;
        int ratio = getCurrentRatio();

        int voidAvail = voidTank.getFluidAmount();
        BlockPos below = pos.below();
        IFluidHandler belowHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP);
        if (belowHandler != null) {
            FluidStack simDrain = belowHandler.drain(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
            voidAvail += simDrain.getAmount();
        }

        int actualChem;
        if (voidAvail >= ratio) {
            actualChem = (int) Math.min(toBudget, voidAvail / (long) ratio);
        } else if (voidAvail > 0) {
            actualChem = Math.max(1, (int) ((long) voidAvail * toBudget / ratio));
            actualChem = Math.min(actualChem, toBudget);
        } else {
            return;
        }

        if (actualChem <= 0) return;
        long pushed = com.yelle233.yuanliuwujin.compat.mekanism.MekCompatBridge.pushChemicalToNeighbor(
                level, pos, dir, chemId, actualChem);
        if (pushed > 0) {
            int voidConsumed = (int) (pushed * ratio);
            consumeVoidFluid(level, pos, voidConsumed);
            fluidBudgetRemaining -= (int) pushed;
        }
    }

    /**
     * 检查底部是否有虚空流体输入
     */
    private boolean hasVoidFluidInputBelow(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP);
        if (handler != null) {
            FluidStack simDrain = handler.drain(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), 1), IFluidHandler.FluidAction.SIMULATE);
            if (!simDrain.isEmpty()) return true;
        }

        handler = level.getCapability(Capabilities.FluidHandler.BLOCK, below, null);
        if (handler != null) {
            FluidStack simDrain = handler.drain(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), 1), IFluidHandler.FluidAction.SIMULATE);
            return !simDrain.isEmpty();
        }

        return false;
    }

    /**
     * 消耗虚空流体：优先从底部输入，不足时从内部储罐消耗
     */
    private int consumeVoidFluid(ServerLevel level, BlockPos pos, int amount) {
        if (amount <= 0) return 0;

        int consumed = 0;

        BlockPos below = pos.below();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP);
        if (handler != null) {
            FluidStack toDrain = new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), amount);
            FluidStack drained = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
            consumed = drained.getAmount();
        }

        if (consumed == 0) {
            handler = level.getCapability(Capabilities.FluidHandler.BLOCK, below, null);
            if (handler != null) {
                FluidStack toDrain = new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), amount);
                FluidStack drained = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
                consumed = drained.getAmount();
            }
        }

        int remaining = amount - consumed;
        if (remaining > 0 && voidTank.getFluidAmount() > 0) {
            int fromTank = Math.min(remaining, voidTank.getFluidAmount());
            voidTank.drain(fromTank, IFluidHandler.FluidAction.EXECUTE);
            consumed += fromTank;
        }

        return consumed;
    }

    private void pullVoidFluidFromBelow(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        int space = voidTank.getCapacity() - voidTank.getFluidAmount();
        if (space <= 0) return;

        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP);
        if (handler != null) {
            FluidStack toDrain = new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), space);
            FluidStack drained = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                voidTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                return;
            }
        }

        handler = level.getCapability(Capabilities.FluidHandler.BLOCK, below, null);
        if (handler != null) {
            FluidStack toDrain = new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), space);
            FluidStack drained = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) voidTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private void pushFluidToNeighbor(ServerLevel level, BlockPos pos, Direction dir) {
        BlockPos neighbor = pos.relative(dir);
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighbor, dir.getOpposite());
        if (handler == null) return;
        int tickBudget = calcTickBudget(getFaceRate(dir));
        int toBudget = Math.min(tickBudget, fluidBudgetRemaining);
        if (toBudget <= 0) return;
        int ratio = getCurrentRatio();

        long voidAvail = voidTank.getFluidAmount();
        BlockPos below = pos.below();
        IFluidHandler belowHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP);
        if (belowHandler != null) {
            FluidStack simDrain = belowHandler.drain(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
            voidAvail += simDrain.getAmount();
        } else {
            belowHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, below, null);
            if (belowHandler != null) {
                FluidStack simDrain = belowHandler.drain(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
                voidAvail += simDrain.getAmount();
            }
        }

        int actualFluid;
        if (voidAvail >= ratio) {
            actualFluid = (int) Math.min(toBudget, voidAvail / (long) ratio);
        } else if (voidAvail > 0) {
            actualFluid = Math.max(1, (int) (voidAvail * toBudget / ratio));
            actualFluid = Math.min(actualFluid, toBudget);
        } else {
            return;
        }

        if (actualFluid <= 0) return;
        FluidStack toFill = makeOutputFluid(actualFluid);
        if (toFill == null) return;
        int filled = handler.fill(toFill, IFluidHandler.FluidAction.SIMULATE);
        if (filled <= 0) return;
        filled = handler.fill(makeOutputFluid(filled), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) return;

        int voidNeeded = filled * ratio;
        consumeVoidFluid(level, pos, voidNeeded);
        fluidBudgetRemaining -= filled;
    }

    public FluidStack extractForSide(int maxAmount, IFluidHandler.FluidAction action, Direction dir) {
        if (!canWork()) return FluidStack.EMPTY;
        if (fluidBudgetRemaining <= 0) return FluidStack.EMPTY;

        int ratio = getCurrentRatio();

        long voidAvail = voidTank.getFluidAmount();
        if (level instanceof ServerLevel serverLevel) {
            BlockPos below = worldPosition.below();
            IFluidHandler belowHandler = serverLevel.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP);
            if (belowHandler != null) {
                FluidStack simDrain = belowHandler.drain(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
                voidAvail += simDrain.getAmount();
            } else {
                belowHandler = serverLevel.getCapability(Capabilities.FluidHandler.BLOCK, below, null);
                if (belowHandler != null) {
                    FluidStack simDrain = belowHandler.drain(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
                    voidAvail += simDrain.getAmount();
                }
            }
        }

        int actualAmount;
        if (voidAvail >= ratio) {
            actualAmount = Math.min(maxAmount, Math.min(fluidBudgetRemaining, (int) (voidAvail / ratio)));
        } else if (voidAvail > 0) {
            actualAmount = Math.max(1, (int) (voidAvail * Math.min(maxAmount, fluidBudgetRemaining) / ratio));
            actualAmount = Math.min(actualAmount, Math.min(maxAmount, fluidBudgetRemaining));
        } else {
            return FluidStack.EMPTY;
        }

        if (actualAmount <= 0) return FluidStack.EMPTY;
        FluidStack fluid = makeOutputFluid(actualAmount);
        if (fluid == null) return FluidStack.EMPTY;

        if (action.execute() && level instanceof ServerLevel serverLevel) {
            int voidNeeded = fluid.getAmount() * ratio;
            consumeVoidFluid(serverLevel, worldPosition, voidNeeded);
            fluidBudgetRemaining -= fluid.getAmount();
        }
        return fluid;
    }

    @Nullable private FluidStack makeOutputFluid(int amount) {
        if (amount <= 0) return null;
        ItemStack cs = coreSlot.getStackInSlot(0);
        if (cs.isEmpty()) return null;
        if (InfiniteCoreItem.getBindType(cs) != BindType.FLUID) return null;
        ResourceLocation fluidId = InfiniteCoreItem.getBoundFluid(cs);
        if (fluidId == null) return null;
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        if (fluid == null) return null;
        if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
        return new FluidStack(fluid, amount);
    }

    private int calcTotalOutputBudgetThisTick() {
        int total = 0;
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            SideMode mode = getSideMode(dir);
            if (mode == SideMode.BOTH || mode == SideMode.PULL)
                total += calcTickBudget(getFaceRate(dir));
        }
        return total;
    }

    private int calcRequiredFE() {
        long fe = Modconfigs.INFINITE_FE_BASE.get();
        int coeff = Modconfigs.INFINITE_FE_PER_MB_RATE.get();
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (getSideMode(dir) != SideMode.OFF) {
                fe += (long) Math.max(1, getFaceRate(dir) / 20) * coeff;
            }
        }
        return (int) Math.min(fe, Integer.MAX_VALUE - 1);
    }

    public int getCurrentRatio() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        return Modconfigs.getInfiniteRatio(InfiniteCoreItem.getLevel(cs), InfiniteCoreItem.isOverclocked(cs));
    }

    /** 爆炸：删除核心，炸出弹坑，填充虚空流体 */
    private void triggerExplosion(ServerLevel level, BlockPos pos) {
        coreSlot.setStackInSlot(0, ItemStack.EMPTY);
        level.removeBlock(pos, false);
        float strength = Modconfigs.EXPLOSION_STRENGTH.get().floatValue();
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                strength, true, Level.ExplosionInteraction.TNT);
        VoidFluidBlock.placeAt(level, pos);
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
            VoidFluidBlock.placeAt(level, target);
        }
    }

    // ── 绑定信息 ──
    public boolean hasValidBinding() { ItemStack cs = coreSlot.getStackInSlot(0); return !cs.isEmpty() && InfiniteCoreItem.hasValidBinding(cs); }
    public BindType getCoreBindType() { ItemStack cs = coreSlot.getStackInSlot(0); return cs.isEmpty() ? BindType.NONE : InfiniteCoreItem.getBindType(cs); }
    @Nullable public ResourceLocation getBoundFluidId() { ItemStack cs = coreSlot.getStackInSlot(0); return cs.isEmpty() ? null : InfiniteCoreItem.getBoundFluid(cs); }
    @Nullable public Fluid getBoundSourceFluid() { ResourceLocation boundId = getBoundFluidId(); if (boundId == null) return null; if (Modconfigs.isFluidBanned(boundId)) return null; Fluid fluid = BuiltInRegistries.FLUID.get(boundId); if (fluid instanceof FlowingFluid ff) fluid = ff.getSource(); return fluid; }
    @Nullable public Component getBoundSubstanceName() {
        BindType type = getCoreBindType();
        if (type == BindType.FLUID) { Fluid fluid = getBoundSourceFluid(); return fluid != null ? fluid.getFluidType().getDescription() : null; }
        else if (type == BindType.CHEMICAL && MekanismChecker.isLoaded()) {
            ResourceLocation chemId = InfiniteCoreItem.getBoundChemical(coreSlot.getStackInSlot(0));
            return chemId != null ? com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper.getChemicalName(chemId) : null;
        }
        return null;
    }
    /** 获取绑定的化学品 */
    @Nullable public Object getBoundChemical() {
        if (!MekanismChecker.isLoaded()) return null;
        ItemStack cs = coreSlot.getStackInSlot(0);
        if (cs.isEmpty()) return null;
        ResourceLocation id = InfiniteCoreItem.getBoundChemical(cs);
        return com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper.getChemical(id);
    }
    public boolean canWork() {
        if (level == null || level.isClientSide) return lastTickCanWork;
        boolean hasCore = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotEmpty = !voidTank.isEmpty();
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        boolean hasEnoughEnergy = energyStorage.getEnergyStored() >= calcRequiredFE();
        return hasCore && voidNotEmpty && anyFaceEnabled && hasValidBinding() && hasEnoughEnergy;
    }

    // ── ICoreMachine ──
    @Override public ItemStackHandler getCoreSlot() { return coreSlot; }
    @Override public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP || dir == Direction.DOWN) return;
        SideMode next = switch (getSideMode(dir)) { case OFF -> SideMode.PULL; case PULL -> SideMode.BOTH; case BOTH -> SideMode.OFF; };
        sideModes.put(dir, next); notifyCapabilityChanged(dir);
    }
    @Override public void onCoreChanged() { if (level == null) return; pressure = 0.0f; setChanged(); syncToClient(); boolean dirty = !getBlockState().getValue(InfiniteFluidMachineBlock.DIRTY); level.setBlock(worldPosition, getBlockState().setValue(InfiniteFluidMachineBlock.DIRTY, dirty), 3); }
    @Override public boolean isValidCoreItem(Item item) { return item instanceof InfiniteCoreItem; }
    @Override public int getFaceRate(Direction dir) { if (dir == Direction.UP || dir == Direction.DOWN) return Integer.MAX_VALUE - 1; return faceRates.getOrDefault(dir, 20); }
    @Override public void adjustFaceRate(Direction dir, int delta) {
        if (dir == Direction.UP || dir == Direction.DOWN) return;
        int current = getFaceRate(dir); long next = (long) current + delta;
        faceRates.put(dir, (int) Math.max(1, Math.min(Integer.MAX_VALUE - 1L, next)));
        setChanged(); syncToClient();
    }
    public SideMode getSideMode(Direction dir) { if (dir == Direction.UP) return SideMode.OFF; if (dir == Direction.DOWN) return SideMode.PULL; return sideModes.getOrDefault(dir, SideMode.OFF); }

    // ── Getter ──
    public FluidTank getVoidTank() { return voidTank; }
    public EnergyStorage getEnergyStorage() { return energyStorage; }
    public float getPressure() { return pressure; }
    public int getLastTickFEConsumed() { return lastTickFEConsumed; }
    @Nullable public Object getInfiniteChemicalOutput() { return chemOutput; }
    public int getFluidBudgetRemaining() { return fluidBudgetRemaining; }

    // ── 红石控制 ──
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

    private void notifyCapabilityChanged(Direction dir) {
        if (level == null) return;
        setChanged();
        syncToClient();
        boolean dirty = !getBlockState().getValue(InfiniteFluidMachineBlock.DIRTY);
        level.setBlock(worldPosition, getBlockState().setValue(InfiniteFluidMachineBlock.DIRTY, dirty), 3);
        if (!level.isClientSide) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    // ── NBT ──
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("core", coreSlot.serializeNBT(registries));
        tag.putInt("energy", energyStorage.getEnergyStored());
        tag.put("voidTank", voidTank.writeToNBT(registries, new CompoundTag()));
        tag.putFloat("pressure", pressure); tag.putBoolean("lastCanWork", lastTickCanWork);
        tag.putInt("lastFE", lastTickFEConsumed);
        CompoundTag modesTag = new CompoundTag(); sideModes.forEach((d, m) -> modesTag.putString(d.getName(), m.name())); tag.put("sideModes", modesTag);
        CompoundTag ratesTag = new CompoundTag(); faceRates.forEach((d, r) -> ratesTag.putInt(d.getName(), r)); tag.put("faceRates", ratesTag);

        tag.putBoolean("hadRedstoneSignal", hadRedstoneSignal);
        CompoundTag savedModesTag = new CompoundTag();
        savedSideModes.forEach((dir, mode) -> savedModesTag.putString(dir.getName(), mode.name()));
        tag.put("savedSideModes", savedModesTag);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("core")) coreSlot.deserializeNBT(registries, tag.getCompound("core"));
        ((MachineEnergyStorage) energyStorage).setEnergy(tag.getInt("energy"));
        rebuildVoidTank();
        if (tag.contains("voidTank")) voidTank.readFromNBT(registries, tag.getCompound("voidTank"));
        pressure = tag.getFloat("pressure"); lastTickCanWork = tag.getBoolean("lastCanWork");
        lastTickFEConsumed = tag.getInt("lastFE");
        CompoundTag modesTag = tag.getCompound("sideModes");
        for (Direction dir : Direction.values()) { if (dir == Direction.UP || dir == Direction.DOWN) continue; String s = modesTag.getString(dir.getName()); if (!s.isEmpty()) { try { sideModes.put(dir, SideMode.valueOf(s)); } catch (IllegalArgumentException ignored) {} } }
        CompoundTag ratesTag = tag.getCompound("faceRates");
        for (Direction dir : Direction.values()) { if (dir == Direction.UP || dir == Direction.DOWN) continue; if (ratesTag.contains(dir.getName())) faceRates.put(dir, Math.max(1, ratesTag.getInt(dir.getName()))); }

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
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    private void syncToClient() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
}
