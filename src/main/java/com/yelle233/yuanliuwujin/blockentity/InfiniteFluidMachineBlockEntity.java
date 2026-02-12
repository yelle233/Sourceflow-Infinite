package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.InfiniteFluidMachineBlock;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.InfiniteChemicalOutput;
import com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.BindType;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.EnumMap;

/**
 * 无限流体机器的方块实体。
 * <p>
 * 核心功能：
 * <ul>
 *   <li>插入已绑定流体/化学品的核心后，无限产出该物质</li>
 *   <li>五个面各有独立的输出模式：OFF / PULL / BOTH</li>
 *   <li>PULL = 仅允许外部抽取；BOTH = 允许抽取 + 主动推送</li>
 *   <li>工作需从顶面输入 FE 能量</li>
 *   <li>支持 Mekanism 化学品（可选联动，Mekanism 不在时自动跳过）</li>
 * </ul>
 */
public class InfiniteFluidMachineBlockEntity extends BlockEntity {

    /* ====== 类成员变量区域 ====== */

    //记录上一 tick 机器是否处于能工作的状态
    private boolean lastTickCanWork = false;

    private long chemPullBudgetRemaining = 0;

    /** 本 tick 剩余可被抽取量（每 tick 重置） */
    private int pullBudgetRemaining = 0;

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
        SideMode next = switch (getSideMode(dir)) {
            case OFF  -> SideMode.PULL;
            case PULL -> SideMode.BOTH;
            case BOTH -> SideMode.OFF;
        };
        sideModes.put(dir, next);
        notifyCapabilityChanged(dir);
    }

    /* ====== 核心槽 ====== */

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

    public ItemStackHandler getCoreSlot() {
        return coreSlot;
    }

    /* ====== 绑定类型查询 ====== */

    /** 获取核心的绑定类型 */
    public BindType getCoreBindType() {
        ItemStack core = coreSlot.getStackInSlot(0);
        if (core.isEmpty()) return BindType.NONE;
        return InfiniteCoreItem.getBindType(core);
    }

    /** 获取绑定的化学品 ID（仅当核心绑定了化学品时返回非 null） */
    @Nullable
    public ResourceLocation getBoundChemicalId() {
        ItemStack core = coreSlot.getStackInSlot(0);
        if (core.isEmpty()) return null;
        return InfiniteCoreItem.getBoundChemical(core);
    }

    /* ====== 无限流体输出 Handler ====== */

    private final IFluidHandler infiniteOutput = new IFluidHandler() {
        @Override
        public int getTanks() {
            return canWorkNow() ? 1 : 0;
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
            if (f == null || resource.isEmpty() || resource.getFluid() != f) return FluidStack.EMPTY;

            int amt = Math.min(resource.getAmount(), pullBudgetRemaining);
            if (amt <= 0) return FluidStack.EMPTY;

            if (action.execute()) {
                pullBudgetRemaining -= amt;
            }
            return new FluidStack(f, amt);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (!canWorkNow()) return FluidStack.EMPTY;
            Fluid f = getBoundSourceFluid();
            if (f == null || maxDrain <= 0) return FluidStack.EMPTY;

            int amt = Math.min(maxDrain, pullBudgetRemaining);
            if (amt <= 0) return FluidStack.EMPTY;

            if (action.execute()) {
                pullBudgetRemaining -= amt;
            }
            return new FluidStack(f, amt);
        }
    };

    public IFluidHandler getInfiniteOutput() {
        return infiniteOutput;
    }

    /* ====== 无限化学品输出 Handler（Mekanism 联动） ====== */

    /**
     * Mekanism 化学品输出 Handler（懒初始化）。
     * <p>
     * 由于 {@link InfiniteChemicalOutput} 引用了 Mekanism API 类，
     * 此字段只在 Mekanism 加载时才会被实例化，保证不触发 ClassNotFoundException。
     */
    private Object chemicalOutputHandler = null;

    /**
     * 获取 Mekanism 化学品输出 Handler。
     * <p>
     * 返回 Object 类型以避免在没有 Mekanism 时触发类加载。
     * 调用方（ModCapabilities）应在确认 Mekanism 存在后将其强转为 IChemicalHandler。
     */
    public Object getInfiniteChemicalOutput() {
        if (chemicalOutputHandler == null && MekanismChecker.isLoaded()) {
            chemicalOutputHandler = new InfiniteChemicalOutput(
                    () -> {
                        ResourceLocation chemId = getBoundChemicalId();
                        return chemId != null ? MekChemicalHelper.getChemical(chemId) : null;
                    },
                    this::canWorkNow,
                    () -> chemPullBudgetRemaining,
                    consumed -> chemPullBudgetRemaining -= consumed
            );
        }
        return chemicalOutputHandler;
    }

    /* ====== 能量系统 ====== */

    private static final int ENERGY_CAPACITY = Integer.MAX_VALUE;
    private int energy = 0;

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
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

    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public boolean consumeFe(int cost) {
        if (cost <= 0) return true;
        if (energy < cost) return false;
        energy -= cost;
        markEnergyDirtyForSync();
        setChanged();
        return true;
    }

    /** 计算每 tick FE 消耗（支持流体和化学品模式） */
    public int calcFePerTick() {
        if (coreSlot.getStackInSlot(0).isEmpty()) return 0;

        BindType type = getCoreBindType();
        if (type == BindType.FLUID) {
            if (getBoundSourceFluid() == null) return 0;
        } else if (type == BindType.CHEMICAL) {
            if (getBoundChemicalId() == null) return 0;
        } else {
            return 0; // 未绑定
        }

        return Modconfigs.FE_PER_TICK.get() + countEnabledFaces() * Modconfigs.FE_PER_ENABLED_FACE_PER_TICK.get();
    }

    public int getFeCostPerTick() {
        return calcFePerTick();
    }

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


    /** 是否有有效绑定（用于决定 capability 是否暴露，不检查电量） */
    public boolean hasValidBinding() {
        if (coreSlot.getStackInSlot(0).isEmpty()) return false;
        BindType type = getCoreBindType();
        if (type == BindType.FLUID) return getBoundSourceFluid() != null;
        if (type == BindType.CHEMICAL) return getBoundChemicalId() != null;
        return false;
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

        // 解决 Mekanism 管道连接滞后问题
        // 判断当前是否满足连接条件 (有核心 + 已绑定 + 电量足够)
        boolean validNow = be.hasValidBinding();
        // 如果 "现在的状态" 和 "上一 tick 的状态" 不一样
        if (validNow != be.lastTickCanWork) {
            // 更新记录
            be.lastTickCanWork = validNow;
            // 清除 capability 缓存，让 Mekanism 管道重新查询
            level.invalidateCapabilities(pos);
            // 通知周围邻居（包括管道）这里发生了变化
            level.updateNeighborsAt(pos, state.getBlock());
        }

        /* 让机器可以在有无限核心的情况下发光 */

        // 1. 检查核心槽位是否有无限核心
        boolean hasCore = !be.getCoreSlot().getStackInSlot(0).isEmpty();
        // 2. 获取当前方块状态中的 LIT 值
        boolean isLit = state.getValue(InfiniteFluidMachineBlock.LIT);
        // 3. 如果 "实际是否有核心" 和 "方块是否发光" 不一致，则更新方块状态
        if (hasCore != isLit) {
            // 更新 BlockState，保持 LIT 属性与 hasCore 一致
            // flag 3 = Block.UPDATE_ALL (通知客户端更新渲染 + 通知邻居方块)
            level.setBlock(pos, state.setValue(InfiniteFluidMachineBlock.LIT, hasCore), 3);
        }

        BindType bindType = be.getCoreBindType();
        if (bindType == BindType.NONE) return;

        // 计算并消耗 FE
        int cost = be.calcFePerTick();
        if (!be.consumeFe(cost)) return;

        // BOTH 模式面主动推送
        int pushAmount = Modconfigs.BASE_PUSH_PER_TICK.get();

        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            if (be.getSideMode(dir) != SideMode.BOTH) continue;
            if (pushAmount <= 0) continue;

            if (bindType == BindType.FLUID) {
                // 推送流体
                Fluid fluid = be.getBoundSourceFluid();
                if (fluid == null) continue;

                IFluidHandler handler = level.getCapability(
                        Capabilities.FluidHandler.BLOCK, pos.relative(dir), dir.getOpposite());
                if (handler == null) continue;

                if (handler.fill(new FluidStack(fluid, 1), IFluidHandler.FluidAction.SIMULATE) > 0) {
                    handler.fill(new FluidStack(fluid, pushAmount), IFluidHandler.FluidAction.EXECUTE);
                }
            } else if (bindType == BindType.CHEMICAL && MekanismChecker.isLoaded()) {
                // 推送化学品（仅 Mekanism 已加载时）
                ResourceLocation chemId = be.getBoundChemicalId();
                if (chemId != null) {
                    MekChemicalHelper.pushChemical(level, pos, dir, chemId, pushAmount);
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
            ResourceLocation chemId = getBoundChemicalId();
            return chemId != null ? MekChemicalHelper.getChemicalName(chemId) : null;
        }

        return null;
    }

    /* ====== NBT ====== */

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = tag.getInt("Energy");
        if (tag.contains("CoreSlot")) {
            coreSlot.deserializeNBT(registries, tag.getCompound("CoreSlot"));
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
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.put("CoreSlot", coreSlot.serializeNBT(registries));
        CompoundTag modes = new CompoundTag();
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            modes.putString(d.getName(), getSideMode(d).name());
        }
        tag.put("SideModes", modes);
    }

    /* ====== 客户端同步 ====== */

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private int energyLastSynced = Integer.MIN_VALUE;
    private int energySyncCooldown = 0;
    private boolean energyDirtyForSync = false;

    public void markEnergyDirtyForSync() {
        energyDirtyForSync = true;
    }

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
        BlockState st = getBlockState();
        level.sendBlockUpdated(worldPosition, st, st, 3);
        level.invalidateCapabilities(worldPosition);
    }

    /* ====== Capability 变更通知 ====== */

    private void notifyCapabilityChanged(Direction side) {
        if (level == null || level.isClientSide) return;
        setChanged();

        BlockState st = getBlockState();
        level.invalidateCapabilities(worldPosition);

        if (st.getBlock() instanceof InfiniteFluidMachineBlock) {
            boolean dirty = st.getValue(InfiniteFluidMachineBlock.DIRTY);
            st = st.setValue(InfiniteFluidMachineBlock.DIRTY, !dirty);
            level.setBlock(worldPosition, st, 3);
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
}
