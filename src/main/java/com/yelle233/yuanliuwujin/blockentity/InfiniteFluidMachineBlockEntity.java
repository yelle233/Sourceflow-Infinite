package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.InfiniteFluidMachineBlock;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
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
import java.util.Map;

public class InfiniteFluidMachineBlockEntity extends BlockEntity {
    // 3) 默认值（你后面会改成从配置读）
    private static final SideMode DEFAULT_MODE = SideMode.OFF;
    private static final int DEFAULT_PUSH_PER_TICK = 50; // mB/t，先写死，后面接配置

    //六个面的模式：OFF（不输出），PULL（只给能抽的邻居输出），BOTH（给所有邻居输出）
    public enum SideMode { OFF, PULL, BOTH }

    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> sidePushPerTick = new EnumMap<>(Direction.class);

    public SideMode getSideMode(Direction dir) {
        if (dir == Direction.UP) return SideMode.OFF; // 顶面不给出液
        return sideModes.getOrDefault(dir, DEFAULT_MODE); // 先默认
    }


    public void setSideMode(Direction dir, SideMode mode) {
        if (dir == Direction.UP) return;

        SideMode old = getSideMode(dir);
        sideModes.put(dir, mode);
        // 只有真的变化了才通知，减少无意义刷新
        if (old != mode) {
            notifyCapabilityChanged(dir);
        } else {
            setChanged();
        }
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
        // clamp：防止负数/过大（上限你自己定）
        if (v < 0) v = 0;
        if (v > 100_000) v = 100_000;
        return v;
    }

    public void setSidePushPerTick(Direction dir, int amount) {
        if (dir == Direction.UP) return;
        int v = amount;
        if (v < 0) v = 0;
        if (v > 100_000) v = 100_000;
        sidePushPerTick.put(dir, v);
        notifyCapabilityChanged(dir);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            Fluid f = getBoundSourceFluid();
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
    }

    /**
     * 从核心里读取绑定的液体，并强制转换为“源液体（Source）”
     */
    @Nullable
    private Fluid getBoundSourceFluid() {
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



    // ====== 存档（1.21.1 正确签名） ======

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

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



}
