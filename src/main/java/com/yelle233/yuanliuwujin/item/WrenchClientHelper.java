package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.client.RateInputScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 扳手客户端辅助类（1.20.1 Forge 版本）
 * 用于隔离客户端专用代码，避免服务端加载客户端类导致崩溃
 */
@OnlyIn(Dist.CLIENT)
public class WrenchClientHelper {

    /**
     * 打开速率输入界面
     */
    public static void openRateInputScreen(BlockPos pos, Direction face, int currentRate) {
        Minecraft.getInstance().setScreen(new RateInputScreen(pos, face, currentRate));
    }
}
