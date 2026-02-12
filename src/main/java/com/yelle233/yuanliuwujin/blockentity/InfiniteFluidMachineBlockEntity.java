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
 *   <li>插入已绑定液体的无限核心后，可无限产出该液体</li>
 *   <li>五个面（上面除外）各有独立的输出模式：OFF / PULL / BOTH</li>
 *   <li>PULL = 仅允许外部抽取；BOTH = 允许抽取 + 主动推送</li>
 *   <li>工作需消耗 FE 能量（仅从顶面输入）</li>
 * </ul>
 */
public class InfiniteFluidMachineBlockEntity extends BlockEntity {

    /* ====== 面模式枚举 ====== */

    /** 每个面的工作模式 */
    public enum SideMode {
        /** 关闭：不输出 */
        OFF,
        /** 被动：允许外部管道/机器抽取 */
        PULL,
        /** 双向：允许抽取 + 主动向相邻方块推送 */
        BOTH
    }

    /* ====== 面模式管理 ====== */

    /** 五个面（不含 UP）的当前模式 */
    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);

    /** 获取指定面的模式，顶面始终为 OFF */
    public SideMode getSideMode(Direction dir) {
        return dir == Direction.UP ? SideMode.OFF : sideModes.getOrDefault(dir, SideMode.OFF);
    }

    /** 循环切换指定面的模式：OFF → PULL → BOTH → OFF */
    public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP) return;

        SideMode next = switch (getSideMode(dir)) {
            case OFF  -> SideMode.PULL;
            case PULL -> SideMode.BOTH;
            case BOTH -> SideMode.OFF;
        };
        sideModes.put(dir, next);

        // 通知周围方块 capability 已变化
        notifyCapabilityChanged(dir);
    }

    /* ====== 核心槽（1格，仅接受 InfiniteCoreItem） ====== */

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

    /* ====== 无限流体输出 Handler ====== */

    /**
     * 对外暴露的流体 Handler：提供无限量的绑定液体。
     * 不接受外部填入（fill 返回 0），仅支持抽取（drain）。
     */
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
            return false; // 不接受外部填入
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0; // 不接受外部填入
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!canWorkNow()) return FluidStack.EMPTY;
            Fluid f = getBoundSourceFluid();
            if (f == null || resource.isEmpty() || resource.getFluid() != f) return FluidStack.EMPTY;
            return resource.getAmount() > 0 ? new FluidStack(f, resource.getAmount()) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (!canWorkNow()) return FluidStack.EMPTY;
            Fluid f = getBoundSourceFluid();
            if (f == null || maxDrain <= 0) return FluidStack.EMPTY;
            return new FluidStack(f, maxDrain);
        }
    };

    public IFluidHandler getInfiniteOutput() {
        return infiniteOutput;
    }

    /* ====== 能量系统 ====== */

    private static final int ENERGY_CAPACITY = Integer.MAX_VALUE;

    /** 当前存储的 FE */
    private int energy = 0;

    /** 能量接口：仅接收，不可提取 */
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

    /**
     * 消耗指定数量的 FE。
     * @return 是否成功消耗（能量足够）
     */
    public boolean consumeFe(int cost) {
        if (cost <= 0) return true;
        if (energy < cost) return false;
        energy -= cost;
        markEnergyDirtyForSync();
        setChanged();
        return true;
    }

    /** 计算当前每 tick 所需 FE（无核心或液体被ban则返回0） */
    public int calcFePerTick() {
        if (coreSlot.getStackInSlot(0).isEmpty()) return 0;
        if (getBoundSourceFluid() == null) return 0;
        return Modconfigs.FE_PER_TICK.get() + countEnabledFaces() * Modconfigs.FE_PER_ENABLED_FACE_PER_TICK.get();
    }

    /** 供 HUD 显示用 */
    public int getFeCostPerTick() {
        return calcFePerTick();
    }

    /** 机器是否处于可工作状态（有核心 + 液体未被ban + 能量充足） */
    public boolean canWorkNow() {
        int cost = calcFePerTick();
        return cost > 0 && energy >= cost;
    }

    /** 统计已启用（PULL 或 BOTH）的面数量 */
    public int countEnabledFaces() {
        int count = 0;
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            SideMode m = getSideMode(d);
            if (m == SideMode.PULL || m == SideMode.BOTH) count++;
        }
        return count;
    }

    /* ====== 构造器 ====== */

    public InfiniteFluidMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFINITE_FLUID_MACHINE.get(), pos, state);
        // 初始化五个面为 OFF（顶面不参与）
        for (Direction d : Direction.values()) {
            if (d != Direction.UP) sideModes.put(d, SideMode.OFF);
        }
    }

    /* ====== 服务端 Tick ====== */

    public static void serverTick(Level level, BlockPos pos, BlockState state, InfiniteFluidMachineBlockEntity be) {
        be.trySyncEnergyToClient();

        // 无核心或液体被ban → 不工作
        if (be.getCoreSlot().getStackInSlot(0).isEmpty()) return;
        Fluid fluid = be.getBoundSourceFluid();
        if (fluid == null) return;

        // 1) 计算本 tick 的 FE 消耗
        int cost = be.calcFePerTick();

        // 2) 能量不足 → 跳过
        if (!be.consumeFe(cost)) return;

        // 3) 对 BOTH 模式的面主动推送液体
        int pushAmount = Modconfigs.BASE_PUSH_PER_TICK.get();
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            if (be.getSideMode(dir) != SideMode.BOTH) continue;
            if (pushAmount <= 0) continue;

            // 查询相邻方块的流体接收能力
            IFluidHandler handler = level.getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    pos.relative(dir),
                    dir.getOpposite()
            );
            if (handler == null) continue;

            // 先模拟能否填入，再实际填入
            FluidStack toFill = new FluidStack(fluid, pushAmount);
            if (handler.fill(new FluidStack(fluid, 1), IFluidHandler.FluidAction.SIMULATE) > 0) {
                handler.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
            }
        }

        /* 机器是否发光判断,只有在有无限核心的时候才发光 */
        // 检查核心槽位是否有物品
        boolean hasCore = !be.getCoreSlot().getStackInSlot(0).isEmpty();
        // 获取当前方块状态中的 LIT 值
        boolean isLit = state.getValue(InfiniteFluidMachineBlock.LIT);
        // 如果 "实际是否有核心" 和 "方块是否发光" 不一致，则更新方块状态
        if (hasCore != isLit) {
            // 更新 BlockState，保持 LIT 属性与 hasCore 一致
            // flag 3 = Block.UPDATE_ALL (通知客户端更新渲染 + 通知邻居方块)
            level.setBlock(pos, state.setValue(InfiniteFluidMachineBlock.LIT, hasCore), 3);
        }

        be.trySyncEnergyToClient();
    }

    /* ====== 绑定液体解析 ====== */

    /**
     * 从核心物品读取绑定的液体，并转换为源液体形式。
     * 如果液体在 banlist 中，返回 null。
     */
    @Nullable
    public Fluid getBoundSourceFluid() {
        ItemStack core = coreSlot.getStackInSlot(0);
        if (core.isEmpty()) return null;

        ResourceLocation boundId = InfiniteCoreItem.getBoundFluid(core);
        if (boundId == null) return null;

        // 检查 banlist
        if (Modconfigs.isFluidBanned(boundId)) return null;

        // 将流动态液体转换为源液体
        Fluid fluid = BuiltInRegistries.FLUID.get(boundId);
        if (fluid instanceof FlowingFluid ff) {
            fluid = ff.getSource();
        }
        return fluid;
    }

    /* ====== NBT 存档 ====== */

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        energy = tag.getInt("Energy");

        if (tag.contains("CoreSlot")) {
            coreSlot.deserializeNBT(registries, tag.getCompound("CoreSlot"));
        }

        // 读取面模式
        sideModes.clear();
        if (tag.contains("SideModes")) {
            CompoundTag modesTag = tag.getCompound("SideModes");
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                String s = modesTag.getString(d.getName());
                if (!s.isEmpty()) {
                    try {
                        sideModes.put(d, SideMode.valueOf(s));
                    } catch (IllegalArgumentException ignored) {
                        // 无效值则使用默认 OFF
                    }
                }
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.putInt("Energy", energy);
        tag.put("CoreSlot", coreSlot.serializeNBT(registries));

        // 保存面模式
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

    /** 能量同步：限制频率，避免每 tick 都发包 */
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

        if (!energyDirtyForSync && Math.abs(energy - energyLastSynced) < 1) return;

        BlockState st = getBlockState();
        setChanged();
        level.sendBlockUpdated(worldPosition, st, st, 3);
        energyLastSynced = energy;
        energyDirtyForSync = false;
        energySyncCooldown = 5; // 每 5 tick 最多同步一次
    }

    /* ====== 核心变更通知 ====== */

    /** 核心槽内容变化时调用，通知客户端刷新并使 capability 缓存失效 */
    public void onCoreChanged() {
        if (level == null || level.isClientSide) return;
        setChanged();
        BlockState st = getBlockState();
        level.sendBlockUpdated(worldPosition, st, st, 3);
        level.invalidateCapabilities(worldPosition);
    }

    /* ====== Capability 变更通知 ====== */

    /**
     * 面模式切换后，使自身和相邻方块的 capability 缓存失效，
     * 并通过翻转 DIRTY 属性触发方块更新。
     */
    private void notifyCapabilityChanged(Direction side) {
        if (level == null || level.isClientSide) return;
        setChanged();

        BlockState st = getBlockState();
        level.invalidateCapabilities(worldPosition);

        // 翻转 DIRTY 属性以触发 neighbor update
        if (st.getBlock() instanceof InfiniteFluidMachineBlock) {
            boolean dirty = st.getValue(InfiniteFluidMachineBlock.DIRTY);
            st = st.setValue(InfiniteFluidMachineBlock.DIRTY, !dirty);
            level.setBlock(worldPosition, st, 3);
        }

        level.updateNeighborsAt(worldPosition, st.getBlock());

        // 通知相邻方块刷新
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
