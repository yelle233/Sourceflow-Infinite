package com.yelle233.yuanliuwujin.blockentity;

import com.yelle233.yuanliuwujin.block.DestructionMachineBlock;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.DestructionChemicalSink;
import com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper;
import com.yelle233.yuanliuwujin.item.DestructionCoreItem;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.EnumMap;

/**
 * 销毁机器的方块实体。
 * <p>
 * 面模式与无限流体机器完全对称：
 * <ul>
 *   <li>无限机器：PULL = 允许外部抽取 / BOTH = 允许抽取 + 机器主动推送</li>
 *   <li>销毁机器：PUSH = 允许外部推入 / BOTH = 允许推入 + 机器主动抽取</li>
 * </ul>
 * 即 BOTH 在两台机器中都代表"机器主动出力"，PULL/PUSH 代表"只开放被动接口"。
 */
public class DestructionMachineBlockEntity extends BlockEntity implements ICoreMachine {

    private boolean lastTickCanWork = false;
    private int destroyBudgetRemaining = 0;
    private long chemDestroyBudgetRemaining = 0;

    /* ====== 面模式枚举 ====== */

    public enum SideMode {
        /** 面关闭，不对外暴露任何 capability */
        OFF,

        /**
         * 被动接受（Push-in Only）：此面暴露虚空 FluidHandler，
         * 外部管道/容器可主动向此面推送流体，机器将其销毁。
         * 机器自身不会主动抽取。
         * <p>
         * 与无限流体机器的 PULL 对应——两者都只开放被动接口，
         * 不同之处在于方向：无限机器被动"出"，销毁机器被动"入"。
         */
        PUSH,

        /**
         * 主动抽取（Both Active + Passive）：在 PUSH 的基础上，
         * 机器每 tick 还会主动从相邻容器抽取流体并销毁。
         * <p>
         * 与无限流体机器的 BOTH 对应——两者都是"机器主动出力"：
         * 无限机器主动推出流体，销毁机器主动抽入并销毁流体。
         */
        BOTH
    }

    /* ====== 面模式管理 ====== */

    private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);

    public SideMode getSideMode(Direction dir) {
        return dir == Direction.UP ? SideMode.OFF
                : sideModes.getOrDefault(dir, SideMode.OFF);
    }

    @Override
    public void cycleSideMode(Direction dir) {
        if (dir == Direction.UP) return;
        // 循环顺序：OFF → PUSH（被动接受）→ BOTH（主动抽取）→ OFF
        SideMode next = switch (getSideMode(dir)) {
            case OFF  -> SideMode.PUSH;
            case PUSH -> SideMode.BOTH;
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
            return stack.getItem() instanceof DestructionCoreItem;
        }
    };

    @Override
    public ItemStackHandler getCoreSlot() { return coreSlot; }

    @Override
    public boolean isValidCoreItem(Item item) { return item instanceof DestructionCoreItem; }

    public boolean hasCoreInserted() { return !coreSlot.getStackInSlot(0).isEmpty(); }

    /* ====== 虚空流体 Handler ====== */

    private final IFluidHandler voidSink = new IFluidHandler() {
        @Override
        public int getTanks() { return canWorkNow() ? 1 : 0; }

        @Override
        public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }

        @Override
        public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) { return canWorkNow(); }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!canWorkNow() || resource.isEmpty()) return 0;
            int amt = Math.min(resource.getAmount(), destroyBudgetRemaining);
            if (amt <= 0) return 0;
            if (action.execute()) destroyBudgetRemaining -= amt;
            return amt;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    };

    public IFluidHandler getVoidSink() { return voidSink; }

    /* ====== Mekanism 化学品虚空 Handler ====== */

    private Object chemicalSinkHandler = null;

    public Object getChemicalSink() {
        if (chemicalSinkHandler == null && MekanismChecker.isLoaded()) {
            chemicalSinkHandler = new DestructionChemicalSink(
                    this::canWorkNow,
                    () -> chemDestroyBudgetRemaining,
                    consumed -> chemDestroyBudgetRemaining -= consumed
            );
        }
        return chemicalSinkHandler;
    }

    /* ====== 能量系统 ====== */

    private static final int ENERGY_CAPACITY = 1_000_000_000;
    private int energy = 0;

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            int received = Math.min(ENERGY_CAPACITY - energy, maxReceive);
            if (!simulate && received > 0) { energy += received; markEnergyDirtyForSync(); setChanged(); }
            return received;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return ENERGY_CAPACITY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    public IEnergyStorage getEnergyStorage() { return energyStorage; }

    public boolean consumeFe(int cost) {
        if (cost <= 0) return true;
        if (energy < cost) return false;
        energy -= cost;
        markEnergyDirtyForSync();
        setChanged();
        return true;
    }

    public int calcFePerTick() {
        if (!hasCoreInserted()) return 0;
        return Modconfigs.DESTROY_FE_PER_TICK.get()
                + countEnabledFaces() * Modconfigs.DESTROY_FE_PER_ENABLED_FACE.get();
    }

    public int getFeCostPerTick() { return calcFePerTick(); }

    public boolean canWorkNow() {
        int cost = calcFePerTick();
        return cost > 0 && energy >= cost;
    }

    public int countEnabledFaces() {
        int count = 0;
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            SideMode m = getSideMode(d);
            if (m == SideMode.PUSH || m == SideMode.BOTH) count++;
        }
        return count;
    }

    public boolean hasValidBinding() { return hasCoreInserted(); }

    /* ====== 构造器 ====== */

    public DestructionMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DESTRUCTION_MACHINE.get(), pos, state);
        for (Direction d : Direction.values()) {
            if (d != Direction.UP) sideModes.put(d, SideMode.OFF);
        }
    }

    /* ====== 服务端 Tick ====== */

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                   DestructionMachineBlockEntity be) {
        be.trySyncEnergyToClient();

        be.destroyBudgetRemaining     = Modconfigs.DESTROY_PER_TICK.get();
        be.chemDestroyBudgetRemaining = Modconfigs.DESTROY_PER_TICK.get();

        boolean hasCore = be.hasCoreInserted();
        boolean isLit   = state.getValue(DestructionMachineBlock.LIT);
        if (hasCore != isLit) {
            level.setBlock(pos, state.setValue(DestructionMachineBlock.LIT, hasCore), 3);
        }

        if (!hasCore) return;

        boolean validNow = be.hasValidBinding();
        if (validNow != be.lastTickCanWork) {
            be.lastTickCanWork = validNow;
            level.invalidateCapabilities(pos);
            level.updateNeighborsAt(pos, state.getBlock());
        }

        int cost = be.calcFePerTick();
        if (!be.consumeFe(cost)) return;

        // BOTH 模式才主动抽取（PUSH 模式只被动接受，不主动出力）
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            if (be.getSideMode(dir) != SideMode.BOTH) continue;

            // 主动抽取流体并销毁
            if (be.destroyBudgetRemaining > 0) {
                IFluidHandler adjacent = level.getCapability(
                        Capabilities.FluidHandler.BLOCK,
                        pos.relative(dir), dir.getOpposite());
                if (adjacent != null) {
                    FluidStack sim = adjacent.drain(
                            be.destroyBudgetRemaining, IFluidHandler.FluidAction.SIMULATE);
                    if (!sim.isEmpty()) {
                        adjacent.drain(sim.getAmount(), IFluidHandler.FluidAction.EXECUTE);
                        be.destroyBudgetRemaining -= sim.getAmount();
                    }
                }
            }

            // 主动抽取 Mekanism 化学品并销毁
            if (MekanismChecker.isLoaded() && be.chemDestroyBudgetRemaining > 0) {
                long consumed = MekChemicalHelper.drainAndDestroyChemical(
                        level, pos, dir, be.chemDestroyBudgetRemaining);
                be.chemDestroyBudgetRemaining -= consumed;
            }
        }

        be.trySyncEnergyToClient();
    }

    /* ====== NBT ====== */

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = tag.getInt("Energy");
        if (tag.contains("CoreSlot"))
            coreSlot.deserializeNBT(registries, tag.getCompound("CoreSlot"));
        sideModes.clear();
        if (tag.contains("SideModes")) {
            CompoundTag modesTag = tag.getCompound("SideModes");
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                String s = modesTag.getString(d.getName());
                if (!s.isEmpty()) {
                    try { sideModes.put(d, SideMode.valueOf(s)); }
                    catch (IllegalArgumentException ignored) {}
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

    public void markEnergyDirtyForSync() { energyDirtyForSync = true; }

    private void trySyncEnergyToClient() {
        if (level == null || level.isClientSide) return;
        if (energySyncCooldown > 0) { energySyncCooldown--; return; }
        if (!energyDirtyForSync && Math.abs(energy - energyLastSynced) < 1) return;
        BlockState st = getBlockState();
        setChanged();
        level.sendBlockUpdated(worldPosition, st, st, 3);
        energyLastSynced   = energy;
        energyDirtyForSync = false;
        energySyncCooldown = 5;
    }

    @Override
    public void onCoreChanged() {
        if (level == null || level.isClientSide) return;
        setChanged();
        BlockState st = getBlockState();
        level.sendBlockUpdated(worldPosition, st, st, 3);
        level.invalidateCapabilities(worldPosition);
    }

    private void notifyCapabilityChanged(Direction side) {
        if (level == null || level.isClientSide) return;
        setChanged();
        BlockState st = getBlockState();
        level.invalidateCapabilities(worldPosition);
        if (st.getBlock() instanceof DestructionMachineBlock) {
            boolean dirty = st.getValue(DestructionMachineBlock.DIRTY);
            st = st.setValue(DestructionMachineBlock.DIRTY, !dirty);
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

    @Nullable
    public Component getStatusComponent() {
        if (!hasCoreInserted() || !canWorkNow()) return null;
        return Component.translatable("hud.yuanliuwujin.destruction.active");
    }
}
