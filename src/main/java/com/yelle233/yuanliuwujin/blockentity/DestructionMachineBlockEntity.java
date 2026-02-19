package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.DestructionMachineBlock;
import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.DestructionChemicalSink;
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

/**
 * 销毁机器的方块实体（Destruction Machine Block Entity）。
 * <p>
 * <b>工作原理：</b>
 * <ol>
 *   <li>从 4 个侧面（PUSH/BOTH 模式）接收任意流体，按核心等级转换为虚空流体储存</li>
 *   <li>转换公式：每消耗 {@code ratio} mB 任意流体，产生 1 mB 虚空流体</li>
 *   <li>虚空流体储罐满时停止工作；底面向下方主动输出虚空流体（无速率限制）</li>
 *   <li>超频核心运行时会积累压力；压力达到 100% 触发爆炸</li>
 * </ol>
 * <p>
 * <b>面定义：</b>
 * <ul>
 *   <li>UP（顶面）：接受 FE 能量输入</li>
 *   <li>DOWN（底面）：主动输出虚空流体，速率不限</li>
 *   <li>四个侧面：接受任意流体，可独立配置模式（OFF/PUSH/BOTH）和速率</li>
 * </ul>
 */
public class DestructionMachineBlockEntity extends BlockEntity implements ICoreMachine {

    // ── 面模式枚举 ──────────────────────────────────────────────
    public enum SideMode {
        /** 面关闭，不对外暴露 Capability */
        OFF,
        /**
         * 被动接受：此面暴露虚空 FluidHandler，外部管道可向此面推送流体，机器将其销毁。
         * 机器自身不会主动抽取。
         */
        PUSH,
        /**
         * 主动抽取：在 PUSH 基础上，机器每 tick 还会主动从相邻容器抽取流体并销毁。
         */
        BOTH
    }

    // ── 状态变量 ────────────────────────────────────────────────
    /** 上一 tick 机器是否处于工作状态（用于 LIT 状态同步） */
    private boolean lastTickCanWork = false;
    /** 超频压力（0.0–100.0%） */
    private float pressure = 0.0f;

    // ── 面模式和速率 ────────────────────────────────────────────
    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
    /** 各侧面的速率（mB/tick），仅 4 个侧面有效，默认 1 */
    private final EnumMap<Direction, Integer> faceRates = new EnumMap<>(Direction.class);

    // ── 核心槽 ────────────────────────────────────────────────
    private final ItemStackHandler coreSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            onCoreChanged();
        }
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof DestructionCoreItem;
        }
        @Override
        public int getSlotLimit(int slot) { return 1; }
    };

    // ── 能量储存 ───────────────────────────────────────────────
    private final EnergyStorage energyStorage = new EnergyStorage(1_000_000, 100_000, 0) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (!simulate && received > 0) setChanged();
            return received;
        }
    };

    // ── 虚空流体储罐 ───────────────────────────────────────────
    /**
     * 内部虚空流体储罐。仅接受虚空流体，容量由配置文件决定。
     * 底面的 Capability 暴露此储罐。
     */
    private FluidTank voidTank;

    // ── Mekanism 化学品 Sink ────────────────────────────────────
    private DestructionChemicalSink chemSink;
    /** 本 tick 剩余可销毁化学品量 */
    private long chemBudgetRemaining = 0;

    // ── 构造 ──────────────────────────────────────────────────
    public DestructionMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DESTRUCTION_MACHINE.get(), pos, state);
        rebuildVoidTank();
        initFaceRates();
        if (MekanismChecker.isLoaded()) {
            chemSink = new DestructionChemicalSink(
                    this::canWork,
                    () -> chemBudgetRemaining,
                    consumed -> chemBudgetRemaining = Math.max(0, chemBudgetRemaining - consumed)
            );
        }
    }

    private void rebuildVoidTank() {
        int capacity = Modconfigs.MACHINE_VOID_TANK_CAPACITY.get();
        voidTank = new FluidTank(capacity) {
            @Override
            protected void onContentsChanged() { setChanged(); }
            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                // 只接受虚空流体
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

    // ── Tick 逻辑 ─────────────────────────────────────────────

    public static void tick(Level level, BlockPos pos, BlockState state,
                             DestructionMachineBlockEntity be) {
        if (level.isClientSide) return;
        be.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // 1) 计算工作条件
        boolean hasCore = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotFull = voidTank.getFluidAmount() < voidTank.getCapacity();
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        int requiredFE = calcRequiredFE();
        boolean hasEnergy = energyStorage.getEnergyStored() >= requiredFE;

        boolean canWork = hasCore && voidNotFull && hasEnergy && anyFaceEnabled;

        // 2) 重置化学品预算
        if (MekanismChecker.isLoaded()) {
            chemBudgetRemaining = canWork ? Integer.MAX_VALUE : 0;
        }

        if (canWork) {
            // 3) 消耗能量
            energyStorage.extractEnergy(requiredFE, false);

            // 4) BOTH 模式：主动从相邻容器抽取流体并转换为虚空流体
            for (Direction dir : new Direction[]{
                    Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                SideMode mode = getSideMode(dir);
                if (mode != SideMode.BOTH) continue;
                if (voidTank.getFluidAmount() >= voidTank.getCapacity()) break;
                int rate = getFaceRate(dir);
                pullFluidFromNeighbor(level, pos, dir, rate);
            }

            // 5) 主动将虚空流体推送到底面相邻容器
            pushVoidFluidDown(level, pos);

            // 6) 超频压力积累
            ItemStack coreStack = coreSlot.getStackInSlot(0);
            if (DestructionCoreItem.isOverclocked(coreStack)) {
                pressure = (float) Math.min(100.0, pressure + Modconfigs.OVERCLOCK_PRESSURE_PER_TICK.get());
                // 100% 触发爆炸
                if (pressure >= 100.0f) {
                    triggerExplosion(level, pos);
                    return; // 机器已被销毁，不继续执行
                }
            }
        } else {
            // 7) 停机时压力衰减
            if (pressure > 0) {
                pressure = (float) Math.max(0.0, pressure - Modconfigs.PRESSURE_DECAY_PER_TICK.get());
            }
        }

        // 8) 同步 LIT 状态
        if (canWork != lastTickCanWork) {
            lastTickCanWork = canWork;
            level.setBlock(pos, state.setValue(DestructionMachineBlock.LIT, canWork), 3);
        }

        setChanged();
        syncToClient();
    }

    // ── 核心转换：任意流体 → 虚空流体 ─────────────────────────────

    /**
     * 计算本 tick 所需 FE。
     * 基础消耗 + Σ(启用面速率 × 耗电系数)。
     * 使用 long 防止整数溢出。
     */
    private int calcRequiredFE() {
        long fe = Modconfigs.DESTROY_FE_BASE.get();
        int coeff = Modconfigs.DESTROY_FE_PER_MB_RATE.get();
        for (Direction dir : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (getSideMode(dir) != SideMode.OFF) {
                fe += (long) getFaceRate(dir) * coeff;
            }
        }
        return (int) Math.min(fe, Integer.MAX_VALUE - 1);
    }

    /**
     * 从指定方向的相邻容器主动抽取最多 {@code rate} mB 的流体，
     * 并将其按核心等级比例转换为虚空流体注入内部储罐。
     */
    private void pullFluidFromNeighbor(ServerLevel level, BlockPos pos, Direction dir, int rate) {
        BlockPos neighbor = pos.relative(dir);
        IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK, neighbor, dir.getOpposite());
        if (handler == null) return;

        // 尝试抽取，先模拟
        FluidStack simDrain = handler.drain(rate, IFluidHandler.FluidAction.SIMULATE);
        if (simDrain.isEmpty()) return;

        // 计算可转换量（受虚空储罐剩余空间限制）
        ItemStack coreStack = coreSlot.getStackInSlot(0);
        int ratio = Modconfigs.getDestroyRatio(
                DestructionCoreItem.getLevel(coreStack),
                DestructionCoreItem.isOverclocked(coreStack));
        int spaceInMb = voidTank.getCapacity() - voidTank.getFluidAmount();
        // 能转换的虚空量
        int voidCanFit = spaceInMb; // 1 mB 虚空 per ratio mB 任意
        // 对应需要消耗的任意流体
        long maxAnyFluid = (long) voidCanFit * ratio;
        int toDrain = (int) Math.min(simDrain.getAmount(), Math.min(rate, maxAnyFluid));
        if (toDrain <= 0) return;

        // 实际抽取
        FluidStack drained = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;

        // 转换为虚空流体
        int voidProduced = Math.max(1, drained.getAmount() / ratio);
        FluidStack voidFluid = new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), voidProduced);
        voidTank.fill(voidFluid, IFluidHandler.FluidAction.EXECUTE);
    }

    /**
     * 将内部虚空流体主动推送到底面（DOWN）相邻容器，速率不限。
     */
    private void pushVoidFluidDown(ServerLevel level, BlockPos pos) {
        if (voidTank.isEmpty()) return;
        BlockPos below = pos.below();
        IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK, below, Direction.UP);
        if (handler == null) return;

        FluidStack toSend = voidTank.getFluid().copy();
        int filled = handler.fill(toSend, IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            voidTank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    // ── 被动接受（PUSH 模式的 FluidHandler 由 Capability 暴露） ──────

    /**
     * 被动接受：外部管道向此面推送流体时调用。
     * 返回实际消耗量，会按比例转换为虚空流体。
     * 此方法由 ModCapabilities 中注册的匿名 FluidHandler 调用。
     */
    public int acceptFluidFromSide(FluidStack offered) {
        if (!canWork()) return 0;
        ItemStack coreStack = coreSlot.getStackInSlot(0);
        int ratio = Modconfigs.getDestroyRatio(
                DestructionCoreItem.getLevel(coreStack),
                DestructionCoreItem.isOverclocked(coreStack));
        int spaceInMb = voidTank.getCapacity() - voidTank.getFluidAmount();
        int voidCanFit = spaceInMb;
        int maxAccept = (int) Math.min((long) voidCanFit * ratio, offered.getAmount());
        if (maxAccept <= 0) return 0;
        int accepted = maxAccept;
        int voidProduced = Math.max(1, accepted / ratio);
        FluidStack voidFluid = new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), voidProduced);
        voidTank.fill(voidFluid, IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        return accepted;
    }

    /** 面被动接受 FluidHandler，暴露给 ModCapabilities（侧面 PUSH/BOTH 模式） */
    public IFluidHandler makeSideSinkHandler(Direction dir) {
        return new IFluidHandler() {
            @Override public int getTanks() { return canWork() ? 1 : 0; }
            @Override public FluidStack getFluidInTank(int t) { return FluidStack.EMPTY; }
            @Override public int getTankCapacity(int t) { return canWork() ? voidTank.getCapacity() - voidTank.getFluidAmount() : 0; }
            @Override public boolean isFluidValid(int t, FluidStack fs) { return canWork(); }
            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (!canWork() || resource.isEmpty()) return 0;
                int rate = getFaceRate(dir);
                int limited = Math.min(resource.getAmount(), rate);
                FluidStack limited2 = resource.copy();
                limited2.setAmount(limited);
                if (action.simulate()) {
                    // 模拟：只检查空间
                    ItemStack cs = coreSlot.getStackInSlot(0);
                    int ratio = Modconfigs.getDestroyRatio(
                            DestructionCoreItem.getLevel(cs),
                            DestructionCoreItem.isOverclocked(cs));
                    int space = voidTank.getCapacity() - voidTank.getFluidAmount();
                    return (int) Math.min(limited, (long) space * ratio);
                }
                return acceptFluidFromSide(limited2);
            }
            @Override public FluidStack drain(int m, FluidAction a) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack r, FluidAction a) { return FluidStack.EMPTY; }
        };
    }

    // ── 爆炸 ──────────────────────────────────────────────────

    private void triggerExplosion(ServerLevel level, BlockPos pos) {
        // 摧毁机器方块（不掉落）
        level.removeBlock(pos, false);

        // 销毁核心
        coreSlot.setStackInSlot(0, ItemStack.EMPTY);

        // 泄漏虚空流体方块
        int blockCount = Math.min(Modconfigs.EXPLOSION_VOID_BLOCKS.get(),
                voidTank.getFluidAmount() / 1000 + Modconfigs.EXPLOSION_VOID_BLOCKS.get() / 2);
        blockCount = Math.min(blockCount, 64);
        RandomSource rand = level.random;
        for (int i = 0; i < blockCount; i++) {
            int dx = rand.nextIntBetweenInclusive(-3, 3);
            int dy = rand.nextIntBetweenInclusive(-2, 3);
            int dz = rand.nextIntBetweenInclusive(-3, 3);
            BlockPos target = pos.offset(dx, dy, dz);
            VoidFluidBlock.placeAt(level, target);
        }

        // 触发爆炸
        float strength = Modconfigs.EXPLOSION_STRENGTH.get().floatValue();
        level.explode(null,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                strength, true, Level.ExplosionInteraction.BLOCK);
    }

    // ── 工作状态判断 ──────────────────────────────────────────

    public boolean canWork() {
        if (level == null || level.isClientSide) return lastTickCanWork;
        boolean hasCore = !coreSlot.getStackInSlot(0).isEmpty();
        boolean voidNotFull = voidTank.getFluidAmount() < voidTank.getCapacity();
        int requiredFE = calcRequiredFE();
        boolean hasEnergy = energyStorage.getEnergyStored() >= requiredFE;
        boolean anyFaceEnabled = sideModes.values().stream().anyMatch(m -> m != SideMode.OFF);
        return hasCore && voidNotFull && hasEnergy && anyFaceEnabled;
    }

    // ── ICoreMachine 实现 ──────────────────────────────────────

    @Override public ItemStackHandler getCoreSlot() { return coreSlot; }

    @Override
    public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP || dir == Direction.DOWN) return;
        SideMode next = switch (getSideMode(dir)) {
            case OFF  -> SideMode.PUSH;
            case PUSH -> SideMode.BOTH;
            case BOTH -> SideMode.OFF;
        };
        sideModes.put(dir, next);
        notifyCapabilityChanged(dir);
    }

    @Override
    public void onCoreChanged() {
        if (level == null) return;
        pressure = 0.0f; // 核心变化时重置压力
        setChanged();
        syncToClient();
        // 翻转 DIRTY 让相邻方块重新查询 capability
        boolean dirty = !getBlockState().getValue(DestructionMachineBlock.DIRTY);
        level.setBlock(worldPosition,
                getBlockState().setValue(DestructionMachineBlock.DIRTY, dirty), 3);
    }

    @Override
    public boolean isValidCoreItem(Item item) { return item instanceof DestructionCoreItem; }

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

    // ── 面模式 getter ──────────────────────────────────────────

    public SideMode getSideMode(Direction dir) {
        if (dir == Direction.UP) return SideMode.OFF;
        if (dir == Direction.DOWN) return SideMode.PUSH; // 底面固定输出虚空流体
        return sideModes.getOrDefault(dir, SideMode.OFF);
    }

    // ── Capability 失效通知 ────────────────────────────────────

    private void notifyCapabilityChanged(Direction dir) {
        if (level == null) return;
        setChanged();
        syncToClient();
        boolean dirty = !getBlockState().getValue(DestructionMachineBlock.DIRTY);
        level.setBlock(worldPosition,
                getBlockState().setValue(DestructionMachineBlock.DIRTY, dirty), 3);
    }

    // ── Getter（供 HUD、BER、Capability 使用） ──────────────────

    public FluidTank getVoidTank() { return voidTank; }
    public EnergyStorage getEnergyStorage() { return energyStorage; }
    public float getPressure() { return pressure; }

    @Nullable
    public DestructionChemicalSink getChemSink() { return chemSink; }

    // ── NBT 序列化 ────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("core", coreSlot.serializeNBT(registries));
        tag.put("energy", serializeEnergy());
        tag.put("voidTank", voidTank.writeToNBT(registries, new CompoundTag()));
        tag.putFloat("pressure", pressure);
        // 面模式
        CompoundTag modesTag = new CompoundTag();
        sideModes.forEach((dir, mode) -> modesTag.putString(dir.getName(), mode.name()));
        tag.put("sideModes", modesTag);
        // 面速率
        CompoundTag ratesTag = new CompoundTag();
        faceRates.forEach((dir, rate) -> ratesTag.putInt(dir.getName(), rate));
        tag.put("faceRates", ratesTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("core")) coreSlot.deserializeNBT(registries, tag.getCompound("core"));
        if (tag.contains("energy")) deserializeEnergy(tag.getCompound("energy"));
        rebuildVoidTank();
        if (tag.contains("voidTank")) voidTank.readFromNBT(registries, tag.getCompound("voidTank"));
        pressure = tag.getFloat("pressure");
        // 面模式
        CompoundTag modesTag = tag.getCompound("sideModes");
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            String modeStr = modesTag.getString(dir.getName());
            if (!modeStr.isEmpty()) {
                try { sideModes.put(dir, SideMode.valueOf(modeStr)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        // 面速率
        CompoundTag ratesTag = tag.getCompound("faceRates");
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            if (ratesTag.contains(dir.getName())) {
                faceRates.put(dir, Math.max(1, ratesTag.getInt(dir.getName())));
            }
        }
    }

    private CompoundTag serializeEnergy() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("energy", energyStorage.getEnergyStored());
        tag.putInt("capacity", energyStorage.getMaxEnergyStored());
        return tag;
    }

    private void deserializeEnergy(CompoundTag tag) {
        // 使用反射或直接访问（EnergyStorage 是 NeoForge 的，energy 字段为 protected）
        energyStorage.receiveEnergy(tag.getInt("energy"), false);
    }

    // ── 客户端同步 ────────────────────────────────────────────

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
