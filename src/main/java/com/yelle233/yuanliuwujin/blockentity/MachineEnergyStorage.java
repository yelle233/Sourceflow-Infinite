package com.yelle233.yuanliuwujin.blockentity;

import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * 机器专用能量存储。
 * <p>
 * 修复原版 {@link EnergyStorage} 的两个问题：
 * <ul>
 *   <li>maxExtract 不能为 0（否则 extractEnergy 始终返回 0，机器永远不会消耗电力）</li>
 *   <li>NBT 反序列化时需要直接设置能量值而非叠加（否则每次同步都会累加能量）</li>
 * </ul>
 */
public class MachineEnergyStorage extends EnergyStorage {

    private final Runnable onChange;

    public MachineEnergyStorage(Runnable onChange) {
        // capacity = MAX, maxReceive = MAX, maxExtract = MAX
        super(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        this.onChange = onChange;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = super.receiveEnergy(maxReceive, simulate);
        if (!simulate && received > 0) onChange.run();
        return received;
    }

    /**
     * 直接设置能量值（用于 NBT 反序列化），不走 receiveEnergy 叠加逻辑。
     */
    public void setEnergy(int value) {
        this.energy = Math.max(0, Math.min(value, capacity));
    }
}
