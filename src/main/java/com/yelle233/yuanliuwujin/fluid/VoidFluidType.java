package com.yelle233.yuanliuwujin.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * 虚空流体类型定义。
 * <p>
 * 控制虚空流体的视觉与物理属性：
 * <ul>
 *   <li>颜色：深紫色（ARGB 0xCC_200020），半透明</li>
 *   <li>密度/粘度：较高，移动缓慢，符合"危险"的视觉暗示</li>
 *   <li>发光：是（自发光，玩家浸入时仍能看清周围）</li>
 * </ul>
 */
public class VoidFluidType extends FluidType {

    public VoidFluidType(Properties properties) {
        super(properties);
    }

    /**
     * 构建虚空流体类型属性。
     * 可在 {@link com.yelle233.yuanliuwujin.registry.ModFluids} 中直接调用。
     */
    public static FluidType.Properties makeProperties() {
        return FluidType.Properties.create()
                .density(3000)
                .viscosity(6000)
                .temperature(500)
                .lightLevel(4);            // 轻微发光
    }

    /** 流体静止时使用的贴图（仿照原版水的 still 贴图路径） */
    public static final ResourceLocation STILL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("yuanliuwujin", "fluid/void_fluid_still");

    /** 流体流动时使用的贴图 */
    public static final ResourceLocation FLOWING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("yuanliuwujin", "fluid/void_fluid_flow");

    /** 叠加（玩家浸入时屏幕效果）贴图 */
    public static final ResourceLocation OVERLAY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("yuanliuwujin", "fluid/void_fluid_overlay");

    /** 虚空流体颜色：深紫色，ARGB */
    public static final int COLOR_ARGB = 0xCC_1A0030;

    /** 着色向量（用于 getRenderColor 等客户端方法） */
    public static final Vector3f COLOR_VEC = new Vector3f(
            ((COLOR_ARGB >> 16) & 0xFF) / 255f,
            ((COLOR_ARGB >> 8)  & 0xFF) / 255f,
            (COLOR_ARGB         & 0xFF) / 255f
    );

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public net.minecraft.resources.ResourceLocation getStillTexture() {
                return STILL_TEXTURE;
            }

            @Override
            public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                return FLOWING_TEXTURE;
            }

            @Override
            public net.minecraft.resources.ResourceLocation getOverlayTexture() {
                return OVERLAY_TEXTURE;
            }

            @Override
            public int getTintColor(FluidState state, BlockAndTintGetter level, BlockPos pos) {
                return COLOR_ARGB;
            }

            @Override
            public int getTintColor(FluidStack stack) {
                return COLOR_ARGB;
            }
        });
    }
}
