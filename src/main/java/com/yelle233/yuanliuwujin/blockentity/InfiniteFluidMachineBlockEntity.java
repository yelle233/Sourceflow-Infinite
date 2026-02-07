package com.yelle233.yuanliuwujin.blockentity;

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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.EnumMap;

public class InfiniteFluidMachineBlockEntity extends BlockEntity {
    // 先写死默认值，后面做 GUI 时就改它
    public static final int DEFAULT_PUSH_PER_TICK = 50;

    //六个面的模式：OFF（不输出），PULL（只给能抽的邻居输出），BOTH（给所有邻居输出）
    public enum SideMode { OFF, PULL, BOTH }

    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);

    public SideMode getSideMode(Direction dir) {
        if (dir == Direction.UP) return SideMode.OFF; // 顶面不给出液
        return sideModes.getOrDefault(dir, SideMode.BOTH); // 先默认 BOTH，后面再改默认
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
        setChanged();
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
            return f == null ? FluidStack.EMPTY : new FluidStack(f, 1000);
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
    }

    /**
     * 服务端 tick：主动向相邻容器推送 pushPerTick mB
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, InfiniteFluidMachineBlockEntity be) {
        Fluid fluid = be.getBoundSourceFluid();
        if (fluid == null) return;

        int total = be.pushPerTick;
        if (total <= 0) return;

        // 1) 收集可输出的相邻容器
        java.util.ArrayList<IFluidHandler> outputs = new java.util.ArrayList<>();
        for (Direction dir : Direction.values()) {
            BlockPos np = pos.relative(dir);

            IFluidHandler handler = level.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    np,
                    dir.getOpposite()
            );

            if (handler == null) continue;

            // 先做一次模拟，确认它能接收这种液体（避免白分配）
            int canAccept = handler.fill(new FluidStack(fluid, 1), IFluidHandler.FluidAction.SIMULATE);
            if (canAccept > 0) {
                outputs.add(handler);
            }
        }

        if (outputs.isEmpty()) return;

        // 2) 均分：每个邻居先拿到 share
        int n = outputs.size();
        int share = Math.max(1, total / n); // 至少 1mB，避免 total<n 时全是0
        int remaining = total;

        // 为了处理“有的容器吃不完”，我们最多做几轮再分配（防止死循环）
        for (int round = 0; round < 3 && remaining > 0; round++) {
            boolean anyAccepted = false;

            for (IFluidHandler handler : outputs) {
                if (remaining <= 0) break;

                int offer = Math.min(share, remaining);

                int accepted = handler.fill(new FluidStack(fluid, offer), IFluidHandler.FluidAction.EXECUTE);
                if (accepted > 0) {
                    remaining -= accepted;
                    anyAccepted = true;
                }
            }

            // 如果这一轮没人吃到，说明都满了/不接了，直接结束
            if (!anyAccepted) break;

            // 下一轮把剩余再均分一次（让还没满的继续分到）
            if (remaining > 0) {
                share = Math.max(1, remaining / n);
            }
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
        if (tag.contains("SideModes")) {
            CompoundTag modesTag = tag.getCompound("SideModes");
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                String s = modesTag.getString(d.getName());
                if (!s.isEmpty()) sideModes.put(d, SideMode.valueOf(s));
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
}
