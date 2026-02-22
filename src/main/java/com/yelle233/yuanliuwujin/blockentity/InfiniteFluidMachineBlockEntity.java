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

    private final ItemStackHandler coreSlot = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { setChanged(); onCoreChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof InfiniteCoreItem && InfiniteCoreItem.hasValidBinding(stack);
        }
        @Override public int getSlotLimit(int slot) { return 1; }
    };

    private final EnergyStorage energyStorage = new MachineEnergyStorage(this::setChanged);

    private FluidTank voidTank;
    /** Mekanism chemical output handler, stored as Object to avoid loading Mek classes when Mek is absent */
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
        boolean voidNotEmpty = !voidTank.isEmpty();
        int baseFE = Modconfigs.INFINITE_FE_BASE.get();                   // 待机基础耗电
        int requiredFE = calcRequiredFE();                                // 工作满载耗电（基础 + 面速率）
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        boolean hasEnoughEnergy = energyStorage.getEnergyStored() >= requiredFE;
        boolean canWork = hasCore && voidNotEmpty && anyFaceEnabled && hasValidBinding() && hasEnoughEnergy;

        // ── 三档耗电逻辑 ──
        if (canWork) {
            // 工作中：消耗满载电量，HUD 显示满载耗电
            energyStorage.extractEnergy(requiredFE, false);
            lastTickFEConsumed = requiredFE;
        } else if (hasCore) {
            // 待机中（有核心但无法工作）：消耗待机电量，HUD 显示待机耗电
            int standbyConsume = Math.min(baseFE, energyStorage.getEnergyStored());
            if (standbyConsume > 0) energyStorage.extractEnergy(standbyConsume, false);
            lastTickFEConsumed = baseFE;
        } else {
            // 无核心：不耗电，HUD 显示 0
            lastTickFEConsumed = 0;
        }

        fluidBudgetRemaining = canWork ? calcTotalOutputBudgetThisTick() : 0;

        if (canWork) {
            // BOTH 模式推送流体
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

            // 超频压力
            ItemStack coreStack = coreSlot.getStackInSlot(0);
            if (InfiniteCoreItem.isOverclocked(coreStack)) {
                pressure = (float) Math.min(100.0, pressure + Modconfigs.OVERCLOCK_PRESSURE_PER_TICK.get());
                if (pressure >= 100.0f) { triggerExplosion(level, pos); return; }
            }
        } else {
            if (pressure > 0) pressure = (float) Math.max(0.0, pressure - Modconfigs.PRESSURE_DECAY_PER_TICK.get());
        }

        // LIT 发光状态：有核心就亮，没核心就暗，不受工作状态影响
        BlockState currentState = level.getBlockState(pos);
        boolean currentLit = currentState.getValue(InfiniteFluidMachineBlock.LIT);
        if (hasCore != currentLit) {
            level.setBlock(pos, currentState.setValue(InfiniteFluidMachineBlock.LIT, hasCore), 3);
        }
        lastTickCanWork = canWork;
        setChanged(); syncToClient();
    }

    /** BOTH 模式主动推送化学品到邻居 */
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
        int actualChem = (int) Math.min(toBudget, voidAvail / (long) ratio);
        if (actualChem <= 0) return;
        // 通过桥接类推送化学品
        long pushed = com.yelle233.yuanliuwujin.compat.mekanism.MekCompatBridge.pushChemicalToNeighbor(
                level, pos, dir, chemId, actualChem);
        if (pushed > 0) {
            int voidConsumed = (int) (pushed * ratio);
            voidTank.drain(voidConsumed, IFluidHandler.FluidAction.EXECUTE);
            fluidBudgetRemaining -= (int) pushed;
        }
    }

    private void pullVoidFluidFromBelow(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP);
        if (handler == null) return;
        int space = voidTank.getCapacity() - voidTank.getFluidAmount();
        FluidStack toDrain = new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), space);
        FluidStack drained = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
        if (!drained.isEmpty()) voidTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
    }

    private void pushFluidToNeighbor(ServerLevel level, BlockPos pos, Direction dir) {
        BlockPos neighbor = pos.relative(dir);
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighbor, dir.getOpposite());
        if (handler == null) return;
        int tickBudget = calcTickBudget(getFaceRate(dir));
        int toBudget = Math.min(tickBudget, fluidBudgetRemaining);
        if (toBudget <= 0) return;
        int ratio = getCurrentRatio();
        int voidAvail = voidTank.getFluidAmount();
        int actualFluid = (int) Math.min(toBudget, voidAvail / (long) ratio);
        if (actualFluid <= 0) return;
        FluidStack toFill = makeOutputFluid(actualFluid);
        if (toFill == null) return;
        int filled = handler.fill(toFill, IFluidHandler.FluidAction.SIMULATE);
        if (filled <= 0) return;
        filled = handler.fill(makeOutputFluid(filled), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) return;
        voidTank.drain(filled * ratio, IFluidHandler.FluidAction.EXECUTE);
        fluidBudgetRemaining -= filled;
    }

    public FluidStack extractForSide(int maxAmount, IFluidHandler.FluidAction action, Direction dir) {
        if (!canWork()) return FluidStack.EMPTY;
        if (fluidBudgetRemaining <= 0) return FluidStack.EMPTY;
        FluidStack fluid = makeOutputFluid(Math.min(maxAmount, fluidBudgetRemaining));
        if (fluid == null) return FluidStack.EMPTY;
        int ratio = getCurrentRatio();
        int voidNeeded = fluid.getAmount() * ratio;
        if (voidTank.getFluidAmount() < voidNeeded) {
            int maxByVoid = voidTank.getFluidAmount() / ratio;
            if (maxByVoid <= 0) return FluidStack.EMPTY;
            fluid = makeOutputFluid(maxByVoid);
            if (fluid == null) return FluidStack.EMPTY;
        }
        if (action.execute()) {
            voidTank.drain(fluid.getAmount() * ratio, IFluidHandler.FluidAction.EXECUTE);
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
            if (mode == SideMode.BOTH || mode == SideMode.PULL) total += calcTickBudget(getFaceRate(dir));
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
    /** 获取绑定的化学品（返回 Object 以避免 Mek 不存在时的类加载问题） */
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

    private void notifyCapabilityChanged(Direction dir) {
        if (level == null) return;
        setChanged();
        syncToClient();
        // 翻转 DIRTY 触发方块更新（客户端渲染刷新）
        boolean dirty = !getBlockState().getValue(InfiniteFluidMachineBlock.DIRTY);
        level.setBlock(worldPosition, getBlockState().setValue(InfiniteFluidMachineBlock.DIRTY, dirty), 3);
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
        tag.putInt("energy", energyStorage.getEnergyStored());
        tag.put("voidTank", voidTank.writeToNBT(registries, new CompoundTag()));
        tag.putFloat("pressure", pressure); tag.putBoolean("lastCanWork", lastTickCanWork);
        tag.putInt("lastFE", lastTickFEConsumed);
        CompoundTag modesTag = new CompoundTag(); sideModes.forEach((d, m) -> modesTag.putString(d.getName(), m.name())); tag.put("sideModes", modesTag);
        CompoundTag ratesTag = new CompoundTag(); faceRates.forEach((d, r) -> ratesTag.putInt(d.getName(), r)); tag.put("faceRates", ratesTag);
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
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    private void syncToClient() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
}
