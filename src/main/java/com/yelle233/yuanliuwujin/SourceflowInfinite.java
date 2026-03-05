package com.yelle233.yuanliuwujin;

import com.yelle233.yuanliuwujin.advancement.ModCriteriaTriggers;
import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.registry.ModNetwork;
import com.yelle233.yuanliuwujin.registry.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 源流无尽（Sourceflow Infinite）模组主类
 * <p>
 * 1.20.1 Forge版本 - v2.0核心特性：虚空流体中间媒介、分级核心系统、面速率控制、超频压力机制
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

        // 注册成就触发器
        modEventBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Modconfigs.SPEC);

        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            VoidFluidBlock.clearBirthTimes();
            LOGGER.debug("Cleared VoidFluidBlock birth times on server stopping.");
        });
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModCriteriaTriggers::register);
    }
}
