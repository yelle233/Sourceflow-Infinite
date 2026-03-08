package com.yelle233.yuanliuwujin.blockentity;

import net.minecraft.core.Direction;

/**
 * 虚空发电机接口
 * 用于扳手配置侧面输出
 */
public interface IVoidGenerator {

    /**
     * 切换侧面输出状态
     */
    void toggleSideOutput(Direction dir);

    /**
     * 获取侧面输出状态
     */
    boolean getSideOutput(Direction dir);

    /**
     * 调整侧面发电速率
     */
    void adjustSideRate(Direction dir, int delta);

    /**
     * 获取侧面发电速率
     */
    int getSideRate(Direction dir);
}
