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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.EnumMap;

public class InfiniteFluidMachineBlockEntity extends BlockEntity {
    // 默认值（后面会改成从配置读）
    private static final SideMode DEFAULT_MODE = SideMode.OFF;
    private static final int DEFAULT_PUSH_PER_TICK = Modconfigs.BASE_PUSH_PER_TICK.get(); // mB/t


    //六个面的模式：OFF（不输出），PULL（只给能抽的邻居输出），BOTH（给所有邻居输出）
    public enum SideMode { OFF, PULL, BOTH }

    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> sidePushPerTick = new EnumMap<>(Direction.class);

    public SideMode getSideMode(Direction dir) {
        if (dir == Direction.UP) return SideMode.OFF; // 顶面不给出液
        return sideModes.getOrDefault(dir, DEFAULT_MODE); // 先默认
    }


    public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP) return;
        SideMode cur = getSideMode(dir);
        SideMode next = switch (cur) {
            case OFF -> SideMode.PULL;
            case PULL -> SideMode.BOTH;
            case BOTH -> SideMode.OFF;
        };
        sideModes.put(dir, next);
        notifyCapabilityChanged(dir);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // 每面输出量（mB/t）
    public int getSidePushPerTick(Direction dir) {
        if (dir == Direction.UP) return 0;
        int v = sidePushPerTick.getOrDefault(dir, DEFAULT_PUSH_PER_TICK);
        // 防止负数/过大
        if (v < 0) v = 0;
//        if (v > 100_000) v = 100_000;
        return v;
    }



    // 1格核心槽
    private final ItemStackHandler coreSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof InfiniteCoreItem;
        }
    };

    // 玩家可调：每 tick 主动输出多少 mB
    private int pushPerTick = DEFAULT_PUSH_PER_TICK;

    // 对外“无限输出”的流体能力（别人来抽永远有）
    private final IFluidHandler infiniteOutput = new IFluidHandler() {
        @Override
        public int getTanks() {
            if (!canWorkNow()) return 0;
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            Fluid f = getBoundSourceFluid();
            if (!canWorkNow()) return FluidStack.EMPTY;
            // 这里只是“显示用”，给个非0数量让管道知道里面是什么
            return f == null ? FluidStack.EMPTY : new FluidStack(f, Integer.MAX_VALUE);
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE; // 表示“无限”
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false; // 只输出，不接收
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0; // 不允许灌入
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!canWorkNow()) return FluidStack.EMPTY;
            Fluid f = getBoundSourceFluid();
            if (f == null) return FluidStack.EMPTY;

            if (resource.isEmpty() || resource.getFluid() != f) return FluidStack.EMPTY;
            int amt = resource.getAmount();
            if (amt <= 0) return FluidStack.EMPTY;

            // 不消耗任何内部存储，所以 action 无论 SIMULATE/EXECUTE 都直接返回
            return new FluidStack(f, amt);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            Fluid f = getBoundSourceFluid();
            if (f == null) return FluidStack.EMPTY;

            if (maxDrain <= 0) return FluidStack.EMPTY;
            return new FluidStack(f, maxDrain);
        }
    };

    // 构造器：初始化每面模式和输出量
    public InfiniteFluidMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFINITE_FLUID_MACHINE.get(), pos, state);
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                sideModes.put(d, DEFAULT_MODE);
                sidePushPerTick.put(d, DEFAULT_PUSH_PER_TICK); // 每面输出量默认值
            }

    }

    /**
     * 服务端 tick：主动向相邻容器推送 pushPerTick mB
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, InfiniteFluidMachineBlockEntity be) {
        // 必须有核心
        if (be.getCoreSlot().getStackInSlot(0).isEmpty()) return;

        // 必须能解析出绑定液体（源液体）
        Fluid f = be.getBoundSourceFluid();
        if (f == null) return;

        // ===== 1) 计算本 tick 需要的 FE：全 OFF 也要低耗电 =====
        // enabled 面 = PULL 或 BOTH
        final int IDLE_FE_PER_TICK = 2;            // 全 OFF 时也消耗的 FE（你可改成配置）
        final int FE_PER_ENABLED_FACE = 8;         // 每启用一个面额外 FE（PULL/BOTH 同价）
        int enabledFaces = 0;

        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            SideMode m = be.getSideMode(d);
            if (m == SideMode.PULL || m == SideMode.BOTH) enabledFaces++;
        }

        int cost = IDLE_FE_PER_TICK + enabledFaces * FE_PER_ENABLED_FACE;

        // ===== 2) 没电就不工作（不推送）=====
        // 这里假设你 BE 里有字段 energy（int）或方法能取/扣能量
        // 推荐你在 BE 里封装成：boolean consumeFe(int cost)
        if (!be.consumeFe(cost)) {
            return;
        }

        // ===== 3) 有电：只对 BOTH 面主动推送 =====
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            if (be.getSideMode(dir) != SideMode.BOTH) continue;

            int amount = be.getSidePushPerTick(dir);
            if (amount <= 0) continue;

            BlockPos neighborPos = pos.relative(dir);

            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, dir.getOpposite());
            if (handler == null) continue;

            FluidStack test = new FluidStack(f, 1);
            if (handler.fill(test, IFluidHandler.FluidAction.SIMULATE) <= 0) continue;

            handler.fill(new FluidStack(f, amount), IFluidHandler.FluidAction.EXECUTE);
        }
        be.trySyncEnergyToClient();
    }

    public boolean consumeFe(int cost) {
        if (cost <= 0) return true;
        if (energy < cost) return false;   // energy 是你存 FE 的字段
        energy -= cost;
        markEnergyDirtyForSync();
        setChanged();
        return true;
    }

    /**
     * 从核心里读取绑定的液体，并强制转换为“源液体（Source）”
     */
    @Nullable
    public Fluid getBoundSourceFluid() {
        ItemStack core = coreSlot.getStackInSlot(0);
        if (core.isEmpty()) return null;

        ResourceLocation boundId = InfiniteCoreItem.getBoundFluid(core);
        if (boundId == null) return null;

        Fluid fluid = BuiltInRegistries.FLUID.get(boundId);
        if (fluid instanceof FlowingFluid ff) {
            fluid = ff.getSource();
        }
        // BuiltInRegistries.FLUID.get 对未知 id 会返回默认值或空对象的情况非常少见，这里不额外判空
        return fluid;
    }



    // ====== 存档 ======

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        energy = tag.getInt("Energy");
        if (tag.contains("CoreSlot")) {
            coreSlot.deserializeNBT(registries, tag.getCompound("CoreSlot"));
        }
        if (tag.contains("PushPerTick")) {
            pushPerTick = tag.getInt("PushPerTick");
        }
        // --- SideModes ---
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
                        // 旧存档/非法值：忽略，走默认
                    }
                }
            }
        }

// --- SidePushPerTick ---
        sidePushPerTick.clear();
        if (tag.contains("SidePushPerTick")) {
            CompoundTag pushTag = tag.getCompound("SidePushPerTick");
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                if (pushTag.contains(d.getName())) {
                    sidePushPerTick.put(d, pushTag.getInt(d.getName()));
                }
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.put("CoreSlot", coreSlot.serializeNBT(registries));
        tag.putInt("PushPerTick", pushPerTick);
        tag.putInt("Energy", energy);

        CompoundTag modes = new CompoundTag();
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            modes.putString(d.getName(), getSideMode(d).name());
        }
        tag.put("SideModes", modes);

        CompoundTag pushTag = new CompoundTag();
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            pushTag.putInt(d.getName(), getSidePushPerTick(d));
        }
        tag.put("SidePushPerTick", pushTag);

    }

    // ====== 给外部/能力注册用的 getter ======

    public ItemStackHandler getCoreSlot() {
        return coreSlot;
    }

    public IFluidHandler getInfiniteOutput() {
        return infiniteOutput;
    }

    public int getPushPerTick() {
        return pushPerTick;
    }

    public void setPushPerTick(int v) {
        pushPerTick = Math.max(0, v);
        setChanged();
    }


    //解决OFF->PULL需要重放才生效的问题

    private void notifyCapabilityChanged(Direction side) {
        if (level == null || level.isClientSide) return;

        setChanged();
        BlockState st = getBlockState();

        // 1) 清掉本机 capability 缓存
        level.invalidateCapabilities(worldPosition);

        // 2) 强制触发 neighborChanged：翻转一个无意义的 blockstate 属性
        if (st.getBlock() instanceof InfiniteFluidMachineBlock) {
            boolean dirty = st.getValue(InfiniteFluidMachineBlock.DIRTY);
            BlockState newState = st.setValue(InfiniteFluidMachineBlock.DIRTY, !dirty);
            level.setBlock(worldPosition, newState, 3);
            st = newState;
        }

        // 3) 通知周围方块（Create 泵/管道会因此重算）
        level.updateNeighborsAt(worldPosition, st.getBlock());

        // 4) 额外：对“被改的那一侧”的邻居也推一把（更稳）
        if (side != null && side != Direction.UP) {
            BlockPos npos = worldPosition.relative(side);
            level.invalidateCapabilities(npos);
            BlockState ns = level.getBlockState(npos);
            level.updateNeighborsAt(npos, ns.getBlock());
            level.sendBlockUpdated(npos, ns, ns, 3);
        }

        // 5) 客户端同步（Jade 等）
        level.sendBlockUpdated(worldPosition, st, st, 3);
    }


    //BlockEntity 的“网络同步包”
    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries); // 把 CoreSlot/SideModes/SidePushPerTick 一起塞进去
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }


    // ===== Energy (FE) =====
    private int energy = 0;

    // 先写死，后面接配置
    private static final int ENERGY_CAPACITY =Integer.MAX_VALUE;
    private static final int MAX_RECEIVE_PER_TICK = Integer.MAX_VALUE;

    // 全 OFF 时每 tick 的低耗电（只要插了核心且绑定了液体）
    private static final int IDLE_FE_PER_TICK = 2;

    // 每启用一个面（PULL 或 BOTH）额外增加的耗电
    private static final int FE_PER_ENABLED_FACE_PER_TICK = 8;

    private final net.neoforged.neoforge.energy.IEnergyStorage energyStorage =
            new net.neoforged.neoforge.energy.IEnergyStorage() {
                @Override public int receiveEnergy(int maxReceive, boolean simulate) {
                    if (maxReceive <= 0) return 0;
                    int space = ENERGY_CAPACITY - energy;
                    int received = Math.min(space, Math.min(MAX_RECEIVE_PER_TICK, maxReceive));
                    if (!simulate && received > 0) {
                        energy += received;
                        markEnergyDirtyForSync();
                        setChanged();
                    }
                    return received;
                }
                @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; } // 不允许外部抽
                @Override public int getEnergyStored() { return energy; }
                @Override public int getMaxEnergyStored() { return ENERGY_CAPACITY; }
                @Override public boolean canExtract() { return false; }
                @Override public boolean canReceive() { return true; }

            };

    public net.neoforged.neoforge.energy.IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public int getFeCostPerTick() { return calcFePerTick(); }

    public int countEnabledFaces() {
        int c = 0;
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            SideMode m = getSideMode(d);
            if (m == SideMode.PULL || m == SideMode.BOTH) c++;
        }
        return c;
    }

    public int calcFePerTick() {
        // 没核心/没绑定液体：不耗电也不工作
        if (coreSlot.getStackInSlot(0).isEmpty()) return 0;

        Fluid bound = getBoundSourceFluid();
        if (bound == null) return 0;

        int enabled = countEnabledFaces();
        return IDLE_FE_PER_TICK + enabled * FE_PER_ENABLED_FACE_PER_TICK;
    }

    public boolean canWorkNow() {
        int cost = calcFePerTick();
        // cost==0 代表没有核心/绑定，不算“工作”
        return cost > 0 && energy >= cost;
    }

    public boolean consumeFeForTick() {
        int cost = calcFePerTick();
        if (cost <= 0) return false;
        if (energy < cost) return false;
        energy -= cost;
        setChanged();
        return true;
    }

    //插入核心后更新方块
    public void onCoreChanged() {
        if (level == null || level.isClientSide) return;

        setChanged();
        BlockState st = getBlockState();

        // 这句是让客户端立刻拿到新的 CoreSlot / boundFluid 等数据（HUD/Jade 都靠它）
        level.sendBlockUpdated(worldPosition, st, st, 3);

        // 如果你希望“核心变化也会影响能力/抽取可用性”，可以顺便清缓存
        level.invalidateCapabilities(worldPosition);
    }

    // ===== client sync (energy) =====
    private int energyLastSynced = Integer.MIN_VALUE;
    private int energySyncCooldown = 0;
    private boolean energyDirtyForSync = false;

    public void markEnergyDirtyForSync() {
        energyDirtyForSync = true;
    }

    private void trySyncEnergyToClient() {
        if (level == null || level.isClientSide) return;

        // 节流：每 5 tick 最多同步一次（你可改）
        if (energySyncCooldown > 0) {
            energySyncCooldown--;
            return;
        }

        // 变化阈值：能量变化不大就不发（你可改）
        final int THRESHOLD = 1; // 想更省就设 10/50/100

        if (!energyDirtyForSync && Math.abs(energy - energyLastSynced) < THRESHOLD) {
            return;
        }

        BlockState st = getBlockState();
        setChanged();
        level.sendBlockUpdated(worldPosition, st, st, 3); // 这会发送 BE update packet/tag
        energyLastSynced = energy;
        energyDirtyForSync = false;
        energySyncCooldown = 5;
    }




}
