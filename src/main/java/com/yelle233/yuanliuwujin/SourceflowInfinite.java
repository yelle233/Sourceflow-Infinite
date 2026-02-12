package com.yelle233.yuanliuwujin;

import com.yelle233.yuanliuwujin.registry.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 源流无尽（Sourceflow Infinite）模组主类。
 * <p>
 * 模组功能：提供无限流体产出机器，支持绑定任意液体并无限输出。
 */
@Mod(SourceflowInfinite.MODID)
public class SourceflowInfinite {

    public static final String MODID = "yuanliuwujin";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SourceflowInfinite(IEventBus modEventBus, ModContainer modContainer) {
        // 注册各类注册表到 MOD 事件总线
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModDataComponents.REGISTRAR.register(modEventBus);
        ModTab.TABS.register(modEventBus);

        // 注册 Capability 提供器
        modEventBus.addListener(ModCapabilities::register);

        // 注册服务端配置
        modContainer.registerConfig(ModConfig.Type.SERVER, Modconfigs.SPEC);
    }
}
