package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.DestructionMachineBlock;
import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.DestructionChemicalSink;
import com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper;
import com.yelle233.yuanliuwujin.item.DestructionCoreItem;
import com.yelle233.yuanliuwujin.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.EnumMap;

public class DestructionMachineBlockEntity extends BlockEntity implements ICoreMachine {

    public enum SideMode { OFF, PUSH, BOTH }

    private boolean lastTickCanWork = false;
    private float pressure = 0.0f;
    private int lastTickFEConsumed = 0;
    private int secondTick = 0;
    /** 当前 tick 化学品总预算（所有启用面合计），每 tick 重置 */
    private int chemBudgetRemaining = 0;

    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> faceRates = new EnumMap<>(Direction.class);

    private final ItemStackHandler coreSlot = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { setChanged(); onCoreChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.getItem() instanceof DestructionCoreItem; }
        @Override public int getSlotLimit(int slot) { return 1; }
    };

    private final EnergyStorage energyStorage = new EnergyStorage(Integer.MAX_VALUE, Integer.MAX_VALUE, 0) {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (!simulate && received > 0) setChanged();
            return received;
        }
    };

    private FluidTank voidTank;
    private DestructionChemicalSink chemSink;

    public DestructionMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DESTRUCTION_MACHINE.get(), pos, state);
        rebuildVoidTank();
        initFaceRates();
        if (MekanismChecker.isLoaded()) {
            chemSink = new DestructionChemicalSink(
                    this::canWork,
                    this::getVoidTank,
                    this::getCurrentRatio,
                    this::getChemBudgetRemaining
            );
        }
    }

    public int getChemBudgetRemaining() { return chemBudgetRemaining; }

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

    /** 计算所有启用面本 tick 的化学品总预算 */
    private int calcTotalChemBudget() {
        int total = 0;
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            SideMode mode = getSideMode(dir);
            if (mode != SideMode.OFF) total += calcTickBudget(getFaceRate(dir));
        }
        return total;
    }

    public int getCurrentRatio() {
        ItemStack coreStack = coreSlot.getStackInSlot(0);
        return Modconfigs.getDestroyRatio(DestructionCoreItem.getLevel(coreStack), DestructionCoreItem.isOverclocked(coreStack));
    }

    // ── Tick ──
    public static void tick(Level level, BlockPos pos, BlockState state, DestructionMachineBlockEntity be) {
        if (level.isClientSide) return;
        be.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        secondTick = (secondTick + 1) % 20;

        boolean hasCore = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotFull = voidTank.getFluidAmount() < voidTank.getCapacity();
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        int requiredFE = calcRequiredFE();
        boolean hasEnergy = energyStorage.getEnergyStored() >= requiredFE;
        boolean canWork = hasCore && voidNotFull && hasEnergy && anyFaceEnabled;

        // 重置化学品预算
        chemBudgetRemaining = canWork ? calcTotalChemBudget() : 0;

        if (canWork) {
            energyStorage.extractEnergy(requiredFE, false);
            lastTickFEConsumed = requiredFE;

            // BOTH 模式主动抽取
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                SideMode mode = getSideMode(dir);
                if (mode != SideMode.BOTH) continue;
                if (voidTank.getFluidAmount() >= voidTank.getCapacity()) break;
                int tickBudget = calcTickBudget(getFaceRate(dir));

                // 抽取流体
                pullFluidFromNeighbor(level, pos, dir, tickBudget);

                // 抽取 Mekanism 化学品
                if (MekanismChecker.isLoaded()) {
                    pullChemicalFromNeighbor(level, pos, dir, tickBudget);
                }
            }

            pushVoidFluidDown(level, pos);

            ItemStack coreStack = coreSlot.getStackInSlot(0);
            if (DestructionCoreItem.isOverclocked(coreStack)) {
                pressure = (float) Math.min(100.0, pressure + Modconfigs.OVERCLOCK_PRESSURE_PER_TICK.get());
                if (pressure >= 100.0f) { triggerExplosion(level, pos); return; }
            }
        } else {
            lastTickFEConsumed = 0;
            if (pressure > 0) pressure = (float) Math.max(0.0, pressure - Modconfigs.PRESSURE_DECAY_PER_TICK.get());
        }

        if (canWork != lastTickCanWork) { lastTickCanWork = canWork; level.setBlock(pos, state.setValue(DestructionMachineBlock.LIT, canWork), 3); }
        setChanged(); syncToClient();
    }

    /** 从邻居抽取 Mekanism 化学品并转换为虚空流体 */
    private void pullChemicalFromNeighbor(ServerLevel level, BlockPos pos, Direction dir, int tickBudget) {
        if (!MekanismChecker.isLoaded()) return;
        int ratio = getCurrentRatio();
        int voidSpace = voidTank.getCapacity() - voidTank.getFluidAmount();
        long maxChem = Math.min(tickBudget, (long) voidSpace * ratio);
        if (maxChem <= 0) return;

        long drained = MekChemicalHelper.drainAndDestroyChemical(level, pos, dir, maxChem);
        if (drained > 0) {
            int voidProduced = (int) Math.max(1, drained / ratio);
            voidTank.fill(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), voidProduced), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private int calcRequiredFE() {
        long fe = Modconfigs.DESTROY_FE_BASE.get();
        int coeff = Modconfigs.DESTROY_FE_PER_MB_RATE.get();
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (getSideMode(dir) != SideMode.OFF) fe += (long) Math.max(1, getFaceRate(dir) / 20) * coeff;
        }
        return (int) Math.min(fe, Integer.MAX_VALUE - 1);
    }

    private void pullFluidFromNeighbor(ServerLevel level, BlockPos pos, Direction dir, int rate) {
        BlockPos neighbor = pos.relative(dir);
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighbor, dir.getOpposite());
        if (handler == null) return;
        FluidStack simDrain = handler.drain(rate, IFluidHandler.FluidAction.SIMULATE);
        if (simDrain.isEmpty()) return;
        int ratio = getCurrentRatio();
        int spaceInMb = voidTank.getCapacity() - voidTank.getFluidAmount();
        long maxAnyFluid = (long) spaceInMb * ratio;
        int toDrain = (int) Math.min(simDrain.getAmount(), Math.min(rate, maxAnyFluid));
        if (toDrain <= 0) return;
        FluidStack drained = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;
        int voidProduced = Math.max(1, drained.getAmount() / ratio);
        voidTank.fill(new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), voidProduced), IFluidHandler.FluidAction.EXECUTE);
    }

    private void pushVoidFluidDown(ServerLevel level, BlockPos pos) {
        if (voidTank.isEmpty()) return;
        BlockPos below = pos.below();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP);
        if (handler == null) return;
        FluidStack toSend = voidTank.getFluid().copy();
        int filled = handler.fill(toSend, IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) voidTank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
    }

    /** 被动接受流体并转换为虚空流体 */
    public int acceptFluidFromSide(FluidStack offered, Direction dir) {
        if (!canWork()) return 0;
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

    public IFluidHandler makeSideSinkHandler(Direction dir) {
        return new IFluidHandler() {
            @Override public int getTanks() { return canWork() ? 1 : 0; }
            @Override public FluidStack getFluidInTank(int t) { return FluidStack.EMPTY; }
            @Override public int getTankCapacity(int t) { return canWork() ? voidTank.getCapacity() - voidTank.getFluidAmount() : 0; }
            @Override public boolean isFluidValid(int t, FluidStack fs) { return canWork(); }
            @Override public int fill(FluidStack resource, FluidAction action) {
                if (!canWork() || resource.isEmpty()) return 0;
                int tickBudget = calcTickBudget(getFaceRate(dir));
                int limited = Math.min(resource.getAmount(), tickBudget);
                if (action.simulate()) {
                    int ratio = getCurrentRatio();
                    int space = voidTank.getCapacity() - voidTank.getFluidAmount();
                    return (int) Math.min(limited, (long) space * ratio);
                }
                FluidStack limited2 = resource.copy(); limited2.setAmount(limited);
                return acceptFluidFromSide(limited2, dir);
            }
            @Override public FluidStack drain(int m, FluidAction a) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack r, FluidAction a) { return FluidStack.EMPTY; }
        };
    }

    /** 爆炸：删除核心（不掉落），先炸出弹坑，再填充虚空流体 */
    private void triggerExplosion(ServerLevel level, BlockPos pos) {
        // 先清空核心槽（不会掉落）
        coreSlot.setStackInSlot(0, ItemStack.EMPTY);
        // 移除方块
        level.removeBlock(pos, false);
        // 先爆炸（炸出弹坑）
        float strength = Modconfigs.EXPLOSION_STRENGTH.get().floatValue();
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                strength, true, Level.ExplosionInteraction.TNT);
        // 在弹坑中生成虚空流体（爆炸之后，不会被爆炸破坏）
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

    public boolean canWork() {
        if (level == null || level.isClientSide) return lastTickCanWork;
        boolean hasCore = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotFull = voidTank.getFluidAmount() < voidTank.getCapacity();
        int requiredFE = calcRequiredFE();
        boolean hasEnergy = energyStorage.getEnergyStored() >= requiredFE;
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        return hasCore && voidNotFull && hasEnergy && anyFaceEnabled;
    }

    // ── ICoreMachine ──
    @Override public ItemStackHandler getCoreSlot() { return coreSlot; }
    @Override public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP || dir == Direction.DOWN) return;
        SideMode next = switch (getSideMode(dir)) { case OFF -> SideMode.PUSH; case PUSH -> SideMode.BOTH; case BOTH -> SideMode.OFF; };
        sideModes.put(dir, next); notifyCapabilityChanged(dir);
    }
    @Override public void onCoreChanged() { if (level == null) return; pressure = 0.0f; setChanged(); syncToClient(); boolean dirty = !getBlockState().getValue(DestructionMachineBlock.DIRTY); level.setBlock(worldPosition, getBlockState().setValue(DestructionMachineBlock.DIRTY, dirty), 3); }
    @Override public boolean isValidCoreItem(Item item) { return item instanceof DestructionCoreItem; }
    @Override public int getFaceRate(Direction dir) { if (dir == Direction.UP || dir == Direction.DOWN) return Integer.MAX_VALUE - 1; return faceRates.getOrDefault(dir, 20); }
    @Override public void adjustFaceRate(Direction dir, int delta) {
        if (dir == Direction.UP || dir == Direction.DOWN) return;
        int current = getFaceRate(dir); long next = (long) current + delta;
        faceRates.put(dir, (int) Math.max(1, Math.min(Integer.MAX_VALUE - 1L, next)));
        setChanged(); syncToClient();
    }
    public SideMode getSideMode(Direction dir) { if (dir == Direction.UP) return SideMode.OFF; if (dir == Direction.DOWN) return SideMode.PUSH; return sideModes.getOrDefault(dir, SideMode.OFF); }

    // ── Getter ──
    public FluidTank getVoidTank() { return voidTank; }
    public EnergyStorage getEnergyStorage() { return energyStorage; }
    public float getPressure() { return pressure; }
    public int getLastTickFEConsumed() { return lastTickFEConsumed; }
    @Nullable public DestructionChemicalSink getChemSink() { return chemSink; }

    private void notifyCapabilityChanged(Direction dir) {
        if (level == null) return;
        setChanged();
        syncToClient();
        // 翻转 DIRTY 触发方块更新（客户端渲染刷新）
        boolean dirty = !getBlockState().getValue(DestructionMachineBlock.DIRTY);
        level.setBlock(worldPosition, getBlockState().setValue(DestructionMachineBlock.DIRTY, dirty), 3);
        // 通知 NeoForge Capability 系统本位置的 Capability 已变化，
        // 使相邻的 Mekanism 管道重新检查连接状态（解决 OFF ↔ 启用时管道不自动连接的问题）
        if (!level.isClientSide) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    // ── NBT ──
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("core", coreSlot.serializeNBT(registries));
        CompoundTag eTag = new CompoundTag(); eTag.putInt("energy", energyStorage.getEnergyStored()); tag.put("energy", eTag);
        tag.put("voidTank", voidTank.writeToNBT(registries, new CompoundTag()));
        tag.putFloat("pressure", pressure); tag.putBoolean("lastCanWork", lastTickCanWork);
        tag.putInt("lastFE", lastTickFEConsumed);
        CompoundTag modesTag = new CompoundTag(); sideModes.forEach((dir, mode) -> modesTag.putString(dir.getName(), mode.name())); tag.put("sideModes", modesTag);
        CompoundTag ratesTag = new CompoundTag(); faceRates.forEach((dir, rate) -> ratesTag.putInt(dir.getName(), rate)); tag.put("faceRates", ratesTag);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("core")) coreSlot.deserializeNBT(registries, tag.getCompound("core"));
        if (tag.contains("energy")) energyStorage.receiveEnergy(tag.getCompound("energy").getInt("energy"), false);
        rebuildVoidTank(); if (tag.contains("voidTank")) voidTank.readFromNBT(registries, tag.getCompound("voidTank"));
        pressure = tag.getFloat("pressure"); lastTickCanWork = tag.getBoolean("lastCanWork");
        lastTickFEConsumed = tag.getInt("lastFE");
        CompoundTag modesTag = tag.getCompound("sideModes");
        for (Direction dir : Direction.values()) { if (dir == Direction.UP || dir == Direction.DOWN) continue; String modeStr = modesTag.getString(dir.getName()); if (!modeStr.isEmpty()) { try { sideModes.put(dir, SideMode.valueOf(modeStr)); } catch (IllegalArgumentException ignored) {} } }
        CompoundTag ratesTag = tag.getCompound("faceRates");
        for (Direction dir : Direction.values()) { if (dir == Direction.UP || dir == Direction.DOWN) continue; if (ratesTag.contains(dir.getName())) faceRates.put(dir, Math.max(1, ratesTag.getInt(dir.getName()))); }
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    private void syncToClient() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
}
