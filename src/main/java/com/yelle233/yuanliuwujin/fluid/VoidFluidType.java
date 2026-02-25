package com.yelle233.yuanliuwujin.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * 虚空流体类型定义（1.20.1 Forge 版本）。
 */
public class VoidFluidType extends FluidType {

    public VoidFluidType(Properties properties) {
        super(properties);
    }

    public static FluidType.Properties makeProperties() {
        return FluidType.Properties.create()
                .density(3000)
                .viscosity(6000)
                .temperature(500)
                .lightLevel(4);
    }

    public static final ResourceLocation STILL_TEXTURE =
            new ResourceLocation("yuanliuwujin", "block/void_fluid_still");
    public static final ResourceLocation FLOWING_TEXTURE =
            new ResourceLocation("yuanliuwujin", "block/void_fluid_flow");
    public static final ResourceLocation OVERLAY_TEXTURE =
            new ResourceLocation("yuanliuwujin", "block/void_fluid_overlay");

    public static final int COLOR_ARGB = 0x88_C8A0E8;

    public static final Vector3f COLOR_VEC = new Vector3f(
            ((COLOR_ARGB >> 16) & 0xFF) / 255f,
            ((COLOR_ARGB >> 8)  & 0xFF) / 255f,
            (COLOR_ARGB         & 0xFF) / 255f
    );

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() { return STILL_TEXTURE; }
            @Override
            public ResourceLocation getFlowingTexture() { return FLOWING_TEXTURE; }
            @Override
            public ResourceLocation getOverlayTexture() { return OVERLAY_TEXTURE; }
            @Override
            public int getTintColor(FluidState state, BlockAndTintGetter level, BlockPos pos) { return COLOR_ARGB; }
            @Override
            public int getTintColor(FluidStack stack) { return COLOR_ARGB; }
        });
    }
}

