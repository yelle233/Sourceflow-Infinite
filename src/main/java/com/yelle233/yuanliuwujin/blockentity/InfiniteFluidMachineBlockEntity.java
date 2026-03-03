package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.InfiniteFluidMachineBlock;
import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.BindType;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.MekChemicalKind;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * 无限流体机器方块实体（1.20.1 Forge v2.0 版本）。
 * <p>
 * v2.0 新特性：
 * <ul>
 *   <li>虚空储罐（FluidTank）：机器从下方吸取虚空流体用于产出</li>
 *   <li>面速率控制（faceRates）：每面独立 mB/s 速率，默认 20 mB/s</li>
 *   <li>超频压力机制：OC 核心每 tick 积累 0.006%，达到 100% 时爆炸</li>
 *   <li>分级核心：L1–L4 + OC，转换比由配置决定</li>
 * </ul>
 * <p>
 * 1.20.1 Forge 关键差异：
 * <ul>
 *   <li>使用 {@link LazyOptional} + {@code getCapability()} 暴露 Capability</li>
 *   <li>Mekanism 化学品分四种类型，使用四个独立的 Output Handler</li>
 *   <li>NBT 序列化使用 {@code load()} / {@code saveAdditional()} 无 registries 参数</li>
 *   <li>FluidTank NBT 使用 {@code writeToNBT(tag)} / {@code readFromNBT(tag)} 无 registries 参数</li>
 * </ul>
 */
public class InfiniteFluidMachineBlockEntity extends BlockEntity implements ICoreMachine {

    public enum SideMode { OFF, PULL, BOTH }

    // ── 状态字段 ────────────────────────────────────────────
    private boolean lastTickCanWork = false;
    private float pressure          = 0.0f;
    private int lastTickFEConsumed  = 0;
    private int secondTick          = 0;
    private int fluidBudgetRemaining = 0;

    // ── 面模式与面速率 ──────────────────────────────────────
    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> faceRates  = new EnumMap<>(Direction.class);

    // ── 红石控制相关 ────────────────────────────────────────
    private boolean hadRedstoneSignal = false;
    private final EnumMap<Direction, SideMode> savedSideModes = new EnumMap<>(Direction.class);

    // ── 核心槽 ──────────────────────────────────────────────
    private final ItemStackHandler coreSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) { setChanged(); onCoreChanged(); }
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.getItem() instanceof InfiniteCoreItem && InfiniteCoreItem.hasValidBinding(stack);
        }
        @Override public int getSlotLimit(int slot) { return 1; }
    };

    // ── 能量存储 ────────────────────────────────────────────
    private final MachineEnergyStorage energyStorage = new MachineEnergyStorage(this::setChanged);

    // ── 虚空储罐 ────────────────────────────────────────────
    private FluidTank voidTank;

    // ── Capability LazyOptional ─────────────────────────────
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.empty();
    private LazyOptional<IFluidHandler> voidTankReadCap = LazyOptional.empty();
    /** 每个侧面的流体 Capability，仅 PULL/BOTH 模式下有效 */
    private final EnumMap<Direction, LazyOptional<IFluidHandler>> fluidCaps = new EnumMap<>(Direction.class);
    // Mekanism 化学品输出（四种类型，避免 Mek 未加载时类加载）
    private Object gasOut, infusionOut, pigmentOut, slurryOut;
    private final EnumMap<Direction, LazyOptional<?>> gasCaps      = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, LazyOptional<?>> infusionCaps = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, LazyOptional<?>> pigmentCaps  = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, LazyOptional<?>> slurryCaps   = new EnumMap<>(Direction.class);

    // ── 构造函数 ────────────────────────────────────────────
    public InfiniteFluidMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFINITE_FLUID_MACHINE.get(), pos, state);
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
            faceRates.put(dir, 20);
        }
    }

    private void rebuildCapabilities() {
        // 能量 Capability（顶面接收能量）
        energyCap = LazyOptional.of(() -> energyStorage);
        voidTankReadCap = LazyOptional.of(() -> makeVoidTankReadOnly());
        // 流体 Capability 在 getCapability 中按需创建
        for (Direction dir : Direction.values()) {
            fluidCaps.remove(dir);
        }
        if (MekanismChecker.isLoaded()) buildMekOutputs();
    }

    private void buildMekOutputs() {
        gasOut = new com.yelle233.yuanliuwujin.compat.mekanism.InfiniteGasOutput(
                () -> {
                    if (getBoundChemicalKind() != MekChemicalKind.GAS) return null;
                    ResourceLocation id = getBoundChemicalId();
                    return id != null ? mekanism.api.MekanismAPI.gasRegistry().getValue(id) : null;
                },
                this::canWorkNow,
                this::getVoidTank,
                this::getCurrentRatio,
                () -> fluidBudgetRemaining,
                consumed -> fluidBudgetRemaining = Math.max(0, fluidBudgetRemaining - consumed)
        );
        infusionOut = new com.yelle233.yuanliuwujin.compat.mekanism.InfiniteInfusionOutput(
                () -> {
                    if (getBoundChemicalKind() != MekChemicalKind.INFUSION) return null;
                    ResourceLocation id = getBoundChemicalId();
                    return id != null ? mekanism.api.MekanismAPI.infuseTypeRegistry().getValue(id) : null;
                },
                this::canWorkNow,
                this::getVoidTank,
                this::getCurrentRatio,
                () -> fluidBudgetRemaining,
                consumed -> fluidBudgetRemaining = Math.max(0, fluidBudgetRemaining - consumed)
        );
        pigmentOut = new com.yelle233.yuanliuwujin.compat.mekanism.InfinitePigmentOutput(
                () -> {
                    if (getBoundChemicalKind() != MekChemicalKind.PIGMENT) return null;
                    ResourceLocation id = getBoundChemicalId();
                    return id != null ? mekanism.api.MekanismAPI.pigmentRegistry().getValue(id) : null;
                },
                this::canWorkNow,
                this::getVoidTank,
                this::getCurrentRatio,
                () -> fluidBudgetRemaining,
                consumed -> fluidBudgetRemaining = Math.max(0, fluidBudgetRemaining - consumed)
        );
        slurryOut = new com.yelle233.yuanliuwujin.compat.mekanism.InfiniteSlurryOutput(
                () -> {
                    if (getBoundChemicalKind() != MekChemicalKind.SLURRY) return null;
                    ResourceLocation id = getBoundChemicalId();
                    return id != null ? mekanism.api.MekanismAPI.slurryRegistry().getValue(id) : null;
                },
                this::canWorkNow,
                this::getVoidTank,
                this::getCurrentRatio,
                () -> fluidBudgetRemaining,
                consumed -> fluidBudgetRemaining = Math.max(0, fluidBudgetRemaining - consumed)
        );
    }

    // ── Tick ────────────────────────────────────────────────

    public static void tick(Level level, BlockPos pos, BlockState state, InfiniteFluidMachineBlockEntity be) {
        if (level.isClientSide) return;
        be.serverTick(level, pos, state);
    }

    private void serverTick(Level level, BlockPos pos, BlockState state) {
        secondTick = (secondTick + 1) % 20;

        // 从下方吸取虚空流体
        if (voidTank.getFluidAmount() < voidTank.getCapacity()) {
            pullVoidFluidFromBelow(level, pos);
        }

        boolean hasCore       = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotEmpty  = !voidTank.isEmpty();
        int baseFE            = Modconfigs.INFINITE_FE_BASE.get();
        int requiredFE        = calcRequiredFE();
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        boolean hasEnoughFE   = energyStorage.getEnergyStored() >= requiredFE;
        boolean canWork       = hasCore && voidNotEmpty && anyFaceEnabled && hasValidBinding() && hasEnoughFE;

        // 三档耗电
        // HUD 显示实际耗电档位：工作中显示满载，待机中显示待机基础耗电
        lastTickFEConsumed = hasCore ? (canWork ? requiredFE : baseFE) : 0;
        if (canWork) {
            energyStorage.extractEnergy(requiredFE, false);
        } else if (hasCore) {
            int standby = Math.min(baseFE, energyStorage.getEnergyStored());
            if (standby > 0) energyStorage.extractEnergy(standby, false);
        }

        fluidBudgetRemaining = canWork ? calcTotalOutputBudgetThisTick() : 0;

        if (canWork) {
            // BOTH 模式主动推送
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

            // 超频压力积累
            ItemStack coreStack = coreSlot.getStackInSlot(0);
            if (InfiniteCoreItem.isOverclocked(coreStack)) {
                pressure = (float) Math.min(100.0, pressure + Modconfigs.OVERCLOCK_PRESSURE_PER_TICK.get());
                if (pressure >= 100.0f) { triggerExplosion(level, pos); return; }
            }
        } else {
            if (pressure > 0) pressure = (float) Math.max(0.0, pressure - Modconfigs.PRESSURE_DECAY_PER_TICK.get());
        }

        // LIT 状态
        boolean currentLit = state.getValue(InfiniteFluidMachineBlock.LIT);
        if (hasCore != currentLit) {
            level.setBlock(pos, state.setValue(InfiniteFluidMachineBlock.LIT, hasCore), 3);
        }
        lastTickCanWork = canWork;
        setChanged();
        syncToClient();
    }

    private void pullVoidFluidFromBelow(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        var beBlow = level.getBlockEntity(below);
        if (beBlow == null) return;
        beBlow.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).ifPresent(handler -> {
            int space = voidTank.getCapacity() - voidTank.getFluidAmount();
            FluidStack toDrain = new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), space);
            FluidStack drained = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) voidTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        });
    }

    private void pushFluidToNeighbor(Level level, BlockPos pos, Direction dir) {
        BlockPos neighbor = pos.relative(dir);
        var beNeighbor = level.getBlockEntity(neighbor);
        if (beNeighbor == null) return;
        beNeighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite()).ifPresent(handler -> {
            int tickBudget = calcTickBudget(getFaceRate(dir));
            int budget     = Math.min(tickBudget, fluidBudgetRemaining);
            if (budget <= 0) return;
            int ratio      = getCurrentRatio();
            int voidAvail  = voidTank.getFluidAmount();
            int actual     = (int) Math.min(budget, voidAvail / (long) ratio);
            if (actual <= 0) return;
            FluidStack toFill = makeOutputFluid(actual);
            if (toFill == null) return;
            int sim = handler.fill(toFill, IFluidHandler.FluidAction.SIMULATE);
            if (sim <= 0) return;
            int filled = handler.fill(makeOutputFluid(sim), IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0) {
                voidTank.drain(filled * ratio, IFluidHandler.FluidAction.EXECUTE);
                fluidBudgetRemaining -= filled;
            }
        });
    }

    private void pushChemicalToNeighbor(Level level, BlockPos pos, Direction dir) {
        if (!MekanismChecker.isLoaded()) return;
        ItemStack cs    = coreSlot.getStackInSlot(0);
        ResourceLocation chemId = InfiniteCoreItem.getBoundChemical(cs);
        if (chemId == null) return;
        MekChemicalKind kind = InfiniteCoreItem.getBoundChemicalKind(cs);
        int tickBudget = calcTickBudget(getFaceRate(dir));
        int budget     = Math.min(tickBudget, fluidBudgetRemaining);
        if (budget <= 0) return;
        int ratio      = getCurrentRatio();
        int voidAvail  = voidTank.getFluidAmount();
        int actual     = (int) Math.min(budget, voidAvail / (long) ratio);
        if (actual <= 0) return;
        long pushed    = MekChemicalHelper.pushAnyChemical(level, pos, dir, kind, chemId, actual);
        if (pushed > 0) {
            int voidConsumed = (int)(pushed * ratio);
            voidTank.drain(voidConsumed, IFluidHandler.FluidAction.EXECUTE);
            fluidBudgetRemaining -= (int) pushed;
        }
    }

    /** 被动抽取（PULL/BOTH 面）：外部从此面获取流体 */
    public FluidStack extractForSide(int maxAmount, IFluidHandler.FluidAction action, Direction dir) {
        if (!canWorkNow()) return FluidStack.EMPTY;
        if (fluidBudgetRemaining <= 0) return FluidStack.EMPTY;
        FluidStack fluid = makeOutputFluid(Math.min(maxAmount, fluidBudgetRemaining));
        if (fluid == null) return FluidStack.EMPTY;
        int ratio     = getCurrentRatio();
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
                VoidFluidBlock.placeAt(serverLevel, pos);
            }
        }
    }

    // ── 预算计算 ────────────────────────────────────────────

    private int calcTickBudget(int ratePerSecond) {
        int base = ratePerSecond / 20;
        int remainder = ratePerSecond % 20;
        return (secondTick < remainder) ? base + 1 : base;
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
            if (getSideMode(dir) != SideMode.OFF)
                fe += (long) Math.max(1, getFaceRate(dir) / 20) * coeff;
        }
        return (int) Math.min(fe, Integer.MAX_VALUE - 1);
    }

    // ── 绑定信息 ────────────────────────────────────────────

    public boolean hasValidBinding() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        return !cs.isEmpty() && InfiniteCoreItem.hasValidBinding(cs);
    }

    public BindType getCoreBindType() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        return cs.isEmpty() ? BindType.NONE : InfiniteCoreItem.getBindType(cs);
    }

    @Nullable public ResourceLocation getBoundFluidId() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        return cs.isEmpty() ? null : InfiniteCoreItem.getBoundFluid(cs);
    }

    @Nullable public Fluid getBoundSourceFluid() {
        ResourceLocation boundId = getBoundFluidId();
        if (boundId == null) return null;
        if (Modconfigs.isFluidBanned(boundId)) return null;
        Fluid fluid = BuiltInRegistries.FLUID.get(boundId);
        if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
        return fluid;
    }

    @Nullable public ResourceLocation getBoundChemicalId() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        return cs.isEmpty() ? null : InfiniteCoreItem.getBoundChemical(cs);
    }

    public MekChemicalKind getBoundChemicalKind() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        if (cs.isEmpty()) return MekChemicalKind.GAS;
        return InfiniteCoreItem.getBoundChemicalKind(cs);
    }

    @Nullable public Component getBoundSubstanceName() {
        BindType type = getCoreBindType();
        if (type == BindType.FLUID) {
            Fluid f = getBoundSourceFluid();
            return f != null ? f.getFluidType().getDescription() : null;
        } else if (type == BindType.CHEMICAL && MekanismChecker.isLoaded()) {
            MekChemicalKind kind = getBoundChemicalKind();
            return MekChemicalHelper.getChemicalNameByKind(kind, getBoundChemicalId());
        }
        return null;
    }

    @Nullable private FluidStack makeOutputFluid(int amount) {
        if (amount <= 0) return null;
        ItemStack cs = coreSlot.getStackInSlot(0);
        if (cs.isEmpty() || InfiniteCoreItem.getBindType(cs) != BindType.FLUID) return null;
        ResourceLocation fluidId = InfiniteCoreItem.getBoundFluid(cs);
        if (fluidId == null) return null;
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        if (fluid == null) return null;
        if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
        return new FluidStack(fluid, amount);
    }

    public int getCurrentRatio() {
        ItemStack cs = coreSlot.getStackInSlot(0);
        return Modconfigs.getInfiniteRatio(InfiniteCoreItem.getLevel(cs), InfiniteCoreItem.isOverclocked(cs));
    }

    public boolean canWorkNow() {
        if (level == null || level.isClientSide) return lastTickCanWork;
        boolean hasCore       = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotEmpty  = !voidTank.isEmpty();
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        boolean hasEnoughFE   = energyStorage.getEnergyStored() >= calcRequiredFE();
        return hasCore && voidNotEmpty && anyFaceEnabled && hasValidBinding() && hasEnoughFE;
    }

    // ── ICoreMachine ────────────────────────────────────────

    @Override public ItemStackHandler getCoreSlot() { return coreSlot; }

    @Override
    public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP || dir == Direction.DOWN) return;
        SideMode old  = getSideMode(dir);
        SideMode next = switch (old) {
            case OFF  -> SideMode.PULL;
            case PULL -> SideMode.BOTH;
            case BOTH -> SideMode.OFF;
        };
        sideModes.put(dir, next);
        if ((old == SideMode.OFF) != (next == SideMode.OFF)) {
            notifyCapabilityChanged(dir);
        } else {
            notifyStateOnly(dir);
        }
    }

    @Override
    public void onCoreChanged() {
        if (level == null) return;
        pressure = 0.0f;
        rebuildCapabilities();
        setChanged();
        syncToClient();
        boolean dirty = !getBlockState().getValue(InfiniteFluidMachineBlock.DIRTY);
        level.setBlock(worldPosition, getBlockState().setValue(InfiniteFluidMachineBlock.DIRTY, dirty), 3);
    }

    @Override public boolean isValidCoreItem(Item item) { return item instanceof InfiniteCoreItem; }

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
        if (dir == Direction.DOWN) return SideMode.PULL;
        return sideModes.getOrDefault(dir, SideMode.OFF);
    }

    // ── Capability ──────────────────────────────────────────

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // 能量：顶面接收 + null方向（供 Jade 等信息模组查询）
        if (cap == ForgeCapabilities.ENERGY && (side == Direction.UP || side == null)) {
            return energyCap.cast();
        }

        // 虚空储罐只读（null 方向，供 Jade 等信息模组查询）
        if (cap == ForgeCapabilities.FLUID_HANDLER && side == null) {
            return voidTankReadCap.cast();
        }

        // 流体：PULL/BOTH 模式下，对应面暴露 IFluidHandler（仅允许 extract，不允许 fill）
        if (cap == ForgeCapabilities.FLUID_HANDLER && side != null && side != Direction.UP && side != Direction.DOWN) {
            SideMode mode = getSideMode(side);
            if (mode != SideMode.OFF && getCoreBindType() == BindType.FLUID) {
                return fluidCaps.computeIfAbsent(side, d -> {
                    final Direction sideRef = d;
                    return LazyOptional.of(() -> makeSideFluidHandler(sideRef));
                }).cast();
            }
        }

        // Mekanism 化学品：PULL/BOTH 模式下暴露（仅当绑定了化学品）
        if (MekanismChecker.isLoaded() && side != null && side != Direction.UP && side != Direction.DOWN) {
            SideMode mode = getSideMode(side);
            if (mode != SideMode.OFF && getCoreBindType() == BindType.CHEMICAL) {
                MekChemicalKind kind = getBoundChemicalKind();
                if (cap == MekChemicalHelper.GAS_HANDLER_CAP && kind == MekChemicalKind.GAS && gasOut != null) {
                    return gasCaps.computeIfAbsent(side, d -> LazyOptional.of(() -> gasOut)).cast();
                }
                if (cap == MekChemicalHelper.INFUSION_HANDLER_CAP && kind == MekChemicalKind.INFUSION && infusionOut != null) {
                    return infusionCaps.computeIfAbsent(side, d -> LazyOptional.of(() -> infusionOut)).cast();
                }
                if (cap == MekChemicalHelper.PIGMENT_HANDLER_CAP && kind == MekChemicalKind.PIGMENT && pigmentOut != null) {
                    return pigmentCaps.computeIfAbsent(side, d -> LazyOptional.of(() -> pigmentOut)).cast();
                }
                if (cap == MekChemicalHelper.SLURRY_HANDLER_CAP && kind == MekChemicalKind.SLURRY && slurryOut != null) {
                    return slurryCaps.computeIfAbsent(side, d -> LazyOptional.of(() -> slurryOut)).cast();
                }
            }
        }

        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        voidTankReadCap.invalidate();
        fluidCaps.values().forEach(LazyOptional::invalidate);
        gasCaps.values().forEach(LazyOptional::invalidate);
        infusionCaps.values().forEach(LazyOptional::invalidate);
        pigmentCaps.values().forEach(LazyOptional::invalidate);
        slurryCaps.values().forEach(LazyOptional::invalidate);
    }

    private IFluidHandler makeSideFluidHandler(Direction dir) {
        return new IFluidHandler() {
            @Override public int getTanks() { return canWorkNow() ? 1 : 0; }
            @Override public @NotNull FluidStack getFluidInTank(int tank) {
                FluidStack fluid = makeOutputFluid(Integer.MAX_VALUE);
                return fluid != null ? fluid : FluidStack.EMPTY;
            }
            @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
            @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return false; }
            @Override public int fill(FluidStack resource, @NotNull FluidAction action) { return 0; }
            @Override public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
                return extractForSide(maxDrain, action, dir);
            }
            @Override public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
                if (resource.isEmpty()) return FluidStack.EMPTY;
                FluidStack result = drain(resource.getAmount(), FluidAction.SIMULATE);
                if (result.isEmpty() || !result.isFluidEqual(resource)) return FluidStack.EMPTY;
                return drain(resource.getAmount(), action);
            }
        };
    }

    /**
     * 虚空储罐的只读 Handler（用于 Jade / null 方向查询）。
     * 仅暴露储罐内容和容量，不允许任何 fill/drain 操作。
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

    // ── 状态通知 ────────────────────────────────────────────

    private void notifyCapabilityChanged(Direction dir) {
        if (level == null || level.isClientSide) return;
        // 刷新该方向的 LazyOptional
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
        if (st.getBlock() instanceof InfiniteFluidMachineBlock) {
            boolean dirty = st.getValue(InfiniteFluidMachineBlock.DIRTY);
            level.setBlock(worldPosition, st.setValue(InfiniteFluidMachineBlock.DIRTY, !dirty), 3);
        }
        level.updateNeighborsAt(worldPosition, st.getBlock());
        level.sendBlockUpdated(worldPosition, st, st, 3);
    }

    private void notifyStateOnly(Direction dir) {
        if (level == null || level.isClientSide) return;
        setChanged();
        BlockState st = getBlockState();
        if (st.getBlock() instanceof InfiniteFluidMachineBlock) {
            boolean dirty = st.getValue(InfiniteFluidMachineBlock.DIRTY);
            level.setBlock(worldPosition, st.setValue(InfiniteFluidMachineBlock.DIRTY, !dirty), 3);
        }
        level.sendBlockUpdated(worldPosition, st, st, 3);
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ── Getter ──────────────────────────────────────────────
    public FluidTank getVoidTank()            { return voidTank; }
    public IEnergyStorage getEnergyStorage()   { return energyStorage; }
    public float getPressure()                 { return pressure; }
    public int getLastTickFEConsumed()         { return lastTickFEConsumed; }
    public int getFluidBudgetRemaining()       { return fluidBudgetRemaining; }
    public Object getGasOutput()               { return gasOut; }
    public Object getInfusionOutput()          { return infusionOut; }
    public Object getPigmentOutput()           { return pigmentOut; }
    public Object getSlurryOutput()            { return slurryOut; }

    // ── 红石控制 ──────────────────────────────────────────────
    public boolean hadRedstoneSignal() { return hadRedstoneSignal; }
    public void setRedstoneSignal(boolean signal) { hadRedstoneSignal = signal; }

    /**
     * 切换红石控制：保存当前状态并关闭所有侧面，或恢复之前保存的状态
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
            // 当前全部关闭 → 恢复之前保存的状态
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                SideMode saved = savedSideModes.get(dir);
                if (saved != null && saved != SideMode.OFF) {
                    sideModes.put(dir, saved);
                }
            }
            savedSideModes.clear();
        } else {
            // 当前有侧面开启 → 保存状态并全部关闭
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
        if (tag.contains("core"))   coreSlot.deserializeNBT(tag.getCompound("core"));
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

        // 加载红石控制状态
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

        // 保存红石控制状态
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

