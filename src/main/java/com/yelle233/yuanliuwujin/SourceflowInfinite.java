package com.yelle233.yuanliuwujin;

import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.registry.ModNetwork;
import com.yelle233.yuanliuwujin.registry.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 源流无尽（Sourceflow Infinite）模组主类。
 * <p>
 * 1.20.1 Forge 版本（v2.0 新功能移植）：
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

    public SourceflowInfinite() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 流体类型先于流体注册
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);

        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModTab.TABS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);

        ModNetwork.register();


        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Modconfigs.SPEC);

        //监听服务器关闭事件，清理虚空流体出生时间记录，防止内存泄漏。
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            VoidFluidBlock.clearBirthTimes();
            LOGGER.debug("Cleared VoidFluidBlock birth times on server stopping.");
        });
    }
}
