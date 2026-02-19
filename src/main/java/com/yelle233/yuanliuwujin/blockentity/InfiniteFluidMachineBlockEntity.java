package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.InfiniteFluidMachineBlock;
import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.InfiniteChemicalOutput;
import com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper;
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

/**
 * 无限流体机器的方块实体（Infinite Fluid Machine Block Entity）。
 * <p>
 * <b>工作原理：</b>
 * <ol>
 *   <li>底面（DOWN）从相邻容器主动吸取虚空流体储入内部储罐（速率不限）</li>
 *   <li>消耗储罐中的虚空流体，按核心等级转换为绑定的任意流体输出</li>
 *   <li>转换公式：每消耗 {@code ratio} mB 虚空流体，产出 1 mB 任意流体</li>
 *   <li>四个侧面可独立配置 OFF/PULL/BOTH 和速率</li>
 *   <li>虚空流体储罐为空时停止工作</li>
 * </ol>
 * <p>
 * <b>面定义：</b>
 * <ul>
 *   <li>UP：接受 FE 能量输入</li>
 *   <li>DOWN：主动吸取虚空流体（速率不限）；也接受外部推入虚空流体</li>
 *   <li>四个侧面：输出绑定流体，可配置 OFF/PULL/BOTH 和速率</li>
 * </ul>
 */
public class InfiniteFluidMachineBlockEntity extends BlockEntity implements ICoreMachine {

    // ── 面模式枚举 ──────────────────────────────────────────────
    public enum SideMode { OFF, PULL, BOTH }

    // ── 状态变量 ────────────────────────────────────────────────
    private boolean lastTickCanWork = false;
    private float pressure = 0.0f;

    // ── 面模式和速率 ────────────────────────────────────────────
    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> faceRates = new EnumMap<>(Direction.class);

    // ── 核心槽 ────────────────────────────────────────────────
    private final ItemStackHandler coreSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) { setChanged(); onCoreChanged(); }
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof InfiniteCoreItem
                    && InfiniteCoreItem.hasValidBinding(stack);
        }
        @Override
        public int getSlotLimit(int slot) { return 1; }
    };

    // ── 能量储存 ───────────────────────────────────────────────
    private final EnergyStorage energyStorage = new EnergyStorage(1_000_000, 100_000, 0) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int r = super.receiveEnergy(maxReceive, simulate);
            if (!simulate && r > 0) setChanged();
            return r;
        }
    };

    // ── 虚空流体储罐 ───────────────────────────────────────────
    private FluidTank voidTank;

    // ── Mekanism 化学品输出 ────────────────────────────────────
    private InfiniteChemicalOutput chemOutput;
    private long chemBudgetRemaining = 0;

    // ── 本 tick 输出预算（仅流体，每 tick 重置） ───────────────────
    private int fluidBudgetRemaining = 0;

    // ── 构造 ──────────────────────────────────────────────────
    public InfiniteFluidMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFINITE_FLUID_MACHINE.get(), pos, state);
        rebuildVoidTank();
        initFaceRates();
        if (MekanismChecker.isLoaded()) {
            chemOutput = new InfiniteChemicalOutput(
                    this::getBoundChemical,
                    this::canWork,
                    () -> chemBudgetRemaining,
                    consumed -> chemBudgetRemaining = Math.max(0, chemBudgetRemaining - consumed)
            );
        }
    }

    private void rebuildVoidTank() {
        int capacity = Modconfigs.MACHINE_VOID_TANK_CAPACITY.get();
        voidTank = new FluidTank(capacity) {
            @Override protected void onContentsChanged() { setChanged(); }
            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
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

    // ── Tick ──────────────────────────────────────────────────

    public static void tick(Level level, BlockPos pos, BlockState state,
                             InfiniteFluidMachineBlockEntity be) {
        if (level.isClientSide) return;
        be.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // 1) 底面主动吸取虚空流体（不论是否能工作，只要储罐未满）
        if (voidTank.getFluidAmount() < voidTank.getCapacity()) {
            pullVoidFluidFromBelow(level, pos);
        }

        // 2) 工作条件判断
        boolean hasCore = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotEmpty = !voidTank.isEmpty();
        int requiredFE = calcRequiredFE();
        boolean hasEnergy = energyStorage.getEnergyStored() >= requiredFE;
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        boolean canWork = hasCore && voidNotEmpty && hasEnergy && anyFaceEnabled
                && hasValidBinding();

        // 3) 重置本 tick 预算
        fluidBudgetRemaining = canWork ? calcTotalOutputBudget() : 0;
        if (MekanismChecker.isLoaded()) {
            chemBudgetRemaining = canWork ? Integer.MAX_VALUE : 0;
        }

        if (canWork) {
            // 4) 消耗能量
            energyStorage.extractEnergy(requiredFE, false);

            // 5) BOTH 模式：主动向相邻容器推送绑定流体
            for (Direction dir : new Direction[]{
                    Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (getSideMode(dir) != SideMode.BOTH) continue;
                if (voidTank.isEmpty()) break;
                if (fluidBudgetRemaining <= 0) break;
                pushFluidToNeighbor(level, pos, dir);
            }

            // 6) 超频压力积累
            ItemStack coreStack = coreSlot.getStackInSlot(0);
            if (InfiniteCoreItem.isOverclocked(coreStack)) {
                pressure = (float) Math.min(100.0, pressure + Modconfigs.OVERCLOCK_PRESSURE_PER_TICK.get());
                if (pressure >= 100.0f) {
                    triggerExplosion(level, pos);
                    return;
                }
            }
        } else {
            if (pressure > 0) {
                pressure = (float) Math.max(0.0, pressure - Modconfigs.PRESSURE_DECAY_PER_TICK.get());
            }
        }

        // 7) LIT 同步
        if (canWork != lastTickCanWork) {
            lastTickCanWork = canWork;
            level.setBlock(pos, state.setValue(InfiniteFluidMachineBlock.LIT, canWork), 3);
        }

        setChanged();
        syncToClient();
    }

    // ── 虚空流体吸取（底面） ─────────────────────────────────────

    private void pullVoidFluidFromBelow(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK, below, Direction.UP);
        if (handler == null) return;
        int space = voidTank.getCapacity() - voidTank.getFluidAmount();
        FluidStack toDrain = new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), space);
        FluidStack drained = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
        if (!drained.isEmpty()) {
            voidTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    // ── 绑定流体输出（侧面） ──────────────────────────────────────

    private void pushFluidToNeighbor(ServerLevel level, BlockPos pos, Direction dir) {
        BlockPos neighbor = pos.relative(dir);
        IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK, neighbor, dir.getOpposite());
        if (handler == null) return;

        int rate = getFaceRate(dir);
        int toBudget = Math.min(rate, fluidBudgetRemaining);
        if (toBudget <= 0) return;

        // 计算消耗的虚空流体
        int ratio = getCurrentRatio();
        long voidNeeded = (long) toBudget * ratio;
        int voidAvail = voidTank.getFluidAmount();
        // 受虚空限制：实际能产出的任意流体量
        int actualFluid = (int) Math.min(toBudget, voidAvail / (long) ratio);
        if (actualFluid <= 0) return;

        // 先模拟填充
        FluidStack toFill = makeOutputFluid(actualFluid);
        if (toFill == null) return;
        int filled = handler.fill(toFill, IFluidHandler.FluidAction.SIMULATE);
        if (filled <= 0) return;
        filled = handler.fill(makeOutputFluid(filled), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) return;

        // 消耗虚空流体
        int voidConsumed = filled * ratio;
        voidTank.drain(voidConsumed, IFluidHandler.FluidAction.EXECUTE);
        fluidBudgetRemaining -= filled;
    }

    /**
     * 被动抽取（PULL/BOTH 模式的 FluidHandler 暴露给外部管道）。
     * 外部管道调用此方法从机器抽取绑定流体。
     */
    public FluidStack extractForSide(int maxAmount, IFluidHandler.FluidAction action, Direction dir) {
        if (!canWork()) return FluidStack.EMPTY;
        if (fluidBudgetRemaining <= 0) return FluidStack.EMPTY;
        FluidStack fluid = makeOutputFluid(Math.min(maxAmount, fluidBudgetRemaining));
        if (fluid == null) return FluidStack.EMPTY;
        // 检查虚空储量
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

    @Nullable
    private FluidStack makeOutputFluid(int amount) {
        if (amount <= 0) return null;
        ItemStack cs = coreSlot.getStackInSlot(0);
        if (cs.isEmpty()) return null;
        BindType bindType = InfiniteCoreItem.getBindType(cs);
        if (bindType != BindType.FLUID) return null;
        ResourceLocation fluidId = InfiniteCoreItem.getBoundFluid(cs);
        if (fluidId == null) return null;
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        if (fluid == null) return null;
        if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
        return new FluidStack(fluid, amount);
    }

    /** 计算本 tick 最大可输出的流体总量（所有 BOTH 面速率之和） */
    private int calcTotalOutputBudget() {
        int total = 0;
        for (Direction dir : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            SideMode mode = getSideMode(dir);
            if (mode == SideMode.BOTH) total += getFaceRate(dir);
            else if (mode == SideMode.PULL) total += getFaceRate(dir); // PULL 模式外部可抽
        }
        return total;
    }

    private int calcRequiredFE() {
        long fe = Modconfigs.INFINITE_FE_BASE.get();
        int coeff = Modconfigs.INFINITE_FE_PER_MB_RATE.get();
        for (Direction dir : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (getSideMode(dir) != SideMode.OFF) {
                fe += (long) getFaceRate(dir) * coeff;
            }
        }
        return (int) Math.min(fe, Integer.MAX_VALUE - 1);
    }

    private int getCurrentRatio() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        return Modconfigs.getInfiniteRatio(
                InfiniteCoreItem.getLevel(cs),
                InfiniteCoreItem.isOverclocked(cs));
    }

    // ── 爆炸 ──────────────────────────────────────────────────

    private void triggerExplosion(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        coreSlot.setStackInSlot(0, ItemStack.EMPTY);
        int blockCount = Math.min(Modconfigs.EXPLOSION_VOID_BLOCKS.get(),
                voidTank.getFluidAmount() / 1000 + Modconfigs.EXPLOSION_VOID_BLOCKS.get() / 2);
        blockCount = Math.min(blockCount, 64);
        RandomSource rand = level.random;
        for (int i = 0; i < blockCount; i++) {
            BlockPos target = pos.offset(
                    rand.nextIntBetweenInclusive(-3, 3),
                    rand.nextIntBetweenInclusive(-2, 3),
                    rand.nextIntBetweenInclusive(-3, 3));
            VoidFluidBlock.placeAt(level, target);
        }
        float strength = Modconfigs.EXPLOSION_STRENGTH.get().floatValue();
        level.explode(null,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                strength, true, Level.ExplosionInteraction.BLOCK);
    }

    // ── 绑定信息查询 ──────────────────────────────────────────

    public boolean hasValidBinding() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        if (cs.isEmpty()) return false;
        return InfiniteCoreItem.hasValidBinding(cs);
    }

    public BindType getCoreBindType() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        if (cs.isEmpty()) return BindType.NONE;
        return InfiniteCoreItem.getBindType(cs);
    }

    @Nullable
    public ResourceLocation getBoundFluidId() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        if (cs.isEmpty()) return null;
        return InfiniteCoreItem.getBoundFluid(cs);
    }

    /** 获取绑定的源流体（解析注册表，处理 FlowingFluid） */
    @Nullable
    public Fluid getBoundSourceFluid() {
        ResourceLocation boundId = getBoundFluidId();
        if (boundId == null) return null;
        if (Modconfigs.isFluidBanned(boundId)) return null;
        Fluid fluid = BuiltInRegistries.FLUID.get(boundId);
        if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
        return fluid;
    }

    /**
     * 获取绑定物质的显示名称（支持流体和化学品）。
     * 供 HUD 使用。
     */
    @Nullable
    public Component getBoundSubstanceName() {
        BindType type = getCoreBindType();
        if (type == BindType.FLUID) {
            Fluid fluid = getBoundSourceFluid();
            return fluid != null ? fluid.getFluidType().getDescription() : null;
        } else if (type == BindType.CHEMICAL && MekanismChecker.isLoaded()) {
            ResourceLocation chemId = InfiniteCoreItem.getBoundChemical(coreSlot.getStackInSlot(0));
            return chemId != null ? MekChemicalHelper.getChemicalName(chemId) : null;
        }
        return null;
    }

    @Nullable
    public mekanism.api.chemical.Chemical getBoundChemical() {
        if (!MekanismChecker.isLoaded()) return null;
        ItemStack cs = coreSlot.getStackInSlot(0);
        if (cs.isEmpty()) return null;
        ResourceLocation id = InfiniteCoreItem.getBoundChemical(cs);
        return MekChemicalHelper.getChemical(id);
    }

    public boolean canWork() {
        if (level == null || level.isClientSide) return lastTickCanWork;
        boolean hasCore = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotEmpty = !voidTank.isEmpty();
        int requiredFE = calcRequiredFE();
        boolean hasEnergy = energyStorage.getEnergyStored() >= requiredFE;
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        return hasCore && voidNotEmpty && hasEnergy && anyFaceEnabled && hasValidBinding();
    }

    // ── ICoreMachine ──────────────────────────────────────────

    @Override public ItemStackHandler getCoreSlot() { return coreSlot; }

    @Override
    public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP || dir == Direction.DOWN) return;
        SideMode next = switch (getSideMode(dir)) {
            case OFF  -> SideMode.PULL;
            case PULL -> SideMode.BOTH;
            case BOTH -> SideMode.OFF;
        };
        sideModes.put(dir, next);
        notifyCapabilityChanged(dir);
    }

    @Override
    public void onCoreChanged() {
        if (level == null) return;
        pressure = 0.0f;
        setChanged();
        syncToClient();
        boolean dirty = !getBlockState().getValue(InfiniteFluidMachineBlock.DIRTY);
        level.setBlock(worldPosition,
                getBlockState().setValue(InfiniteFluidMachineBlock.DIRTY, dirty), 3);
    }

    @Override public boolean isValidCoreItem(Item item) { return item instanceof InfiniteCoreItem; }

    @Override
    public int getFaceRate(Direction dir) {
        if (dir == Direction.UP || dir == Direction.DOWN) return Integer.MAX_VALUE - 1;
        return faceRates.getOrDefault(dir, 1);
    }

    @Override
    public void adjustFaceRate(Direction dir, int delta) {
        if (dir == Direction.UP || dir == Direction.DOWN) return;
        int current = getFaceRate(dir);
        long next = (long) current + delta;
        faceRates.put(dir, (int) Math.max(1, Math.min(Integer.MAX_VALUE - 1L, next)));
        setChanged();
        syncToClient();
    }

    public SideMode getSideMode(Direction dir) {
        if (dir == Direction.UP) return SideMode.OFF;
        if (dir == Direction.DOWN) return SideMode.PULL; // 底面固定接受虚空流体
        return sideModes.getOrDefault(dir, SideMode.OFF);
    }

    // ── Getter ─────────────────────────────────────────────────

    public FluidTank getVoidTank() { return voidTank; }
    public EnergyStorage getEnergyStorage() { return energyStorage; }
    public float getPressure() { return pressure; }
    @Nullable public InfiniteChemicalOutput getInfiniteChemicalOutput() { return chemOutput; }
    public int getFluidBudgetRemaining() { return fluidBudgetRemaining; }

    private void notifyCapabilityChanged(Direction dir) {
        if (level == null) return;
        setChanged();
        syncToClient();
        boolean dirty = !getBlockState().getValue(InfiniteFluidMachineBlock.DIRTY);
        level.setBlock(worldPosition,
                getBlockState().setValue(InfiniteFluidMachineBlock.DIRTY, dirty), 3);
    }

    // ── NBT ──────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("core", coreSlot.serializeNBT(registries));
        tag.putInt("energy", energyStorage.getEnergyStored());
        tag.put("voidTank", voidTank.writeToNBT(registries, new CompoundTag()));
        tag.putFloat("pressure", pressure);
        tag.putBoolean("lastCanWork", lastTickCanWork);
        CompoundTag modesTag = new CompoundTag();
        sideModes.forEach((d, m) -> modesTag.putString(d.getName(), m.name()));
        tag.put("sideModes", modesTag);
        CompoundTag ratesTag = new CompoundTag();
        faceRates.forEach((d, r) -> ratesTag.putInt(d.getName(), r));
        tag.put("faceRates", ratesTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("core")) coreSlot.deserializeNBT(registries, tag.getCompound("core"));
        energyStorage.receiveEnergy(tag.getInt("energy"), false);
        rebuildVoidTank();
        if (tag.contains("voidTank")) voidTank.readFromNBT(registries, tag.getCompound("voidTank"));
        pressure = tag.getFloat("pressure");
        lastTickCanWork = tag.getBoolean("lastCanWork");
        CompoundTag modesTag = tag.getCompound("sideModes");
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            String s = modesTag.getString(dir.getName());
            if (!s.isEmpty()) {
                try { sideModes.put(dir, SideMode.valueOf(s)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        CompoundTag ratesTag = tag.getCompound("faceRates");
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            if (ratesTag.contains(dir.getName()))
                faceRates.put(dir, Math.max(1, ratesTag.getInt(dir.getName())));
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
