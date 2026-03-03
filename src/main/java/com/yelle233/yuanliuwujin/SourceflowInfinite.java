package com.yelle233.yuanliuwujin;

import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.registry.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 源流无尽（Sourceflow Infinite）模组主类。
 * <p>
 * v2.0 改动：
 * <ul>
 *   <li>引入虚空流体作为两种机器的中间媒介</li>
 *   <li>核心升级至 1–4 级，超频系统</li>
 *   <li>面速率独立可调</li>
 *   <li>超频压力与爆炸机制</li>
 * </ul>
 */
@Mod(SourceflowInfinite.MODID)
public class SourceflowInfinite {

    public static final String MODID = "yuanliuwujin";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SourceflowInfinite(IEventBus modEventBus, ModContainer modContainer) {
        // 流体类型必须先于流体注册
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);

        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModDataComponents.REGISTRAR.register(modEventBus);
        ModTab.TABS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);

        modEventBus.addListener(ModCapabilities::register);

        modContainer.registerConfig(ModConfig.Type.SERVER, Modconfigs.SPEC);

        // 监听服务器关闭事件，清理虚空流体出生时间记录，防止内存泄漏
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            VoidFluidBlock.clearBirthTimes();
        });
    }
}
