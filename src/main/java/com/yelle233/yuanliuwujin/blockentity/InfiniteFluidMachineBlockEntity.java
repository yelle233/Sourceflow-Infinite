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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

public class InfiniteFluidMachineBlockEntity extends BlockEntity {
    // 先写死默认值，后面做 GUI 时就改它
    public static final int DEFAULT_PUSH_PER_TICK = Integer.MAX_VALUE;

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

    // 对外"无限输出"的流体能力（别人来抽永远有）
    private final IFluidHandler infiniteOutput = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            Fluid f = getBoundSourceFluid();
            // 这里只是"显示用"，给个非0数量让管道知道里面是什么
            return f == null ? FluidStack.EMPTY : new FluidStack(f, Integer.MAX_VALUE);
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE; // 表示"无限"
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
     * 服务端 tick：主动向相邻容器推送液体
     * 优化版：移除轮次限制，更激进的推送策略
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

            // 先做一次模拟，确认它能接收这种液体
            int canAccept = handler.fill(new FluidStack(fluid, 1), IFluidHandler.FluidAction.SIMULATE);
            if (canAccept > 0) {
                outputs.add(handler);
            }
        }

        if (outputs.isEmpty()) return;

        // 2) 均分 + 泵式填充
        int remaining = total;
        int n = outputs.size();

        // 单次 fill 给一个上限，避免传特别大的数（也更符合很多容器的实现习惯）
        final int PER_CALL_MAX = 100_000;
        // 每个容器每 tick 最多循环 fill 次数，防止某些 handler 返回很小值导致死循环/卡顿
        final int PER_HANDLER_GUARD = 64;
        // 进行几轮再分配：第1轮均分，第2/3轮把剩余再均分给还能吃的
        final int ROUNDS = 3;

        for (int round = 0; round < ROUNDS && remaining > 0; round++) {
            int share = Math.max(1, remaining / n);
            boolean anyAcceptedThisRound = false;

            for (IFluidHandler handler : outputs) {
                if (remaining <= 0) break;

                // 该容器本轮最多分到的量
                int budget = Math.min(share, remaining);
                if (budget <= 0) continue;

                int guard = PER_HANDLER_GUARD;
                int consumed = 0;

                // 泵式：同一 tick 内多次 fill，尽量把 budget 填完
                while (consumed < budget && guard-- > 0) {
                    int offer = Math.min(PER_CALL_MAX, budget - consumed);
                    int accepted = handler.fill(new FluidStack(fluid, offer), IFluidHandler.FluidAction.EXECUTE);
                    if (accepted <= 0) break;

                    consumed += accepted;
                    anyAcceptedThisRound = true;
                }

                if (consumed > 0) {
                    remaining -= consumed;
                }
            }

            // 如果这一轮没人能接收，说明都满了/不接了，直接结束
            if (!anyAcceptedThisRound) break;
        }
    }



    /**
     * 从核心里读取绑定的液体，并强制转换为"源液体（Source）"
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
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.put("CoreSlot", coreSlot.serializeNBT(registries));
        tag.putInt("PushPerTick", pushPerTick);
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
