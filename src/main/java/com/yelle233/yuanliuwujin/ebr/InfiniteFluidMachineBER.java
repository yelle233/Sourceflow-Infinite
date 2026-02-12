package com.yelle233.yuanliuwujin.ebr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity.SideMode;
import com.yelle233.yuanliuwujin.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * 无限流体机器的方块实体渲染器（BER）。
 * <p>
 * 渲染两部分内容：
 * <ol>
 *   <li>机器内部旋转的核心方块模型（装有核心时才显示）</li>
 *   <li>各面的模式覆盖层贴图（PULL / BOTH）</li>
 * </ol>
 */
public class InfiniteFluidMachineBER implements BlockEntityRenderer<InfiniteFluidMachineBlockEntity> {

    /** PULL 模式的面贴图 */
    private static final ResourceLocation OVERLAY_PULL =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "textures/block/overlay_pull.png");
    /** BOTH 模式的面贴图 */
    private static final ResourceLocation OVERLAY_BOTH =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "textures/block/overlay_both.png");

    public InfiniteFluidMachineBER(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(InfiniteFluidMachineBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // 1) 渲染内部旋转核心
        renderCoreBlockInside(be, partialTick, poseStack, bufferSource);

        // 2) 渲染各面的模式覆盖层
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;

            SideMode mode = be.getSideMode(dir);
            if (mode == SideMode.OFF) continue;

            ResourceLocation tex = (mode == SideMode.PULL) ? OVERLAY_PULL : OVERLAY_BOTH;
            renderFaceOverlay(poseStack, bufferSource, dir, tex, LightTexture.FULL_BRIGHT, packedOverlay);
        }
    }

    /* ====== 内部核心渲染 ====== */

    /**
     * 在机器中心渲染一个旋转的核心方块模型。
     * 使用 {@link ModBlocks#INFINITE_CORE_BLOCK} 的默认状态，全亮度照明。
     */
    private static void renderCoreBlockInside(InfiniteFluidMachineBlockEntity be, float partialTick,
                                              PoseStack poseStack, MultiBufferSource bufferSource) {
        Level level = be.getLevel();
        if (level == null) return;
        if (be.getCoreSlot().getStackInSlot(0).isEmpty()) return;

        poseStack.pushPose();

        // 平移到方块中心
        poseStack.translate(0.5, 0.5, 0.5);

        // Y 轴持续旋转（每 tick 旋转 2°）
        float time = level.getGameTime() + partialTick;
        poseStack.mulPose(Axis.YP.rotationDegrees((time * 2.0f) % 360.0f));

        // 放大到 2x
        poseStack.scale(2.0f, 2.0f, 2.0f);

        // 使用核心方块的默认 BlockState 渲染
        BlockState coreState = ModBlocks.INFINITE_CORE_BLOCK.get().defaultBlockState();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                coreState, poseStack, bufferSource,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }

    /* ====== 面覆盖层渲染 ====== */

    /**
     * 在指定面上渲染一张半透明的模式指示贴图。
     * 贴图略微偏移到面外侧（z=0.501）以避免 Z-fighting。
     */
    private static void renderFaceOverlay(PoseStack poseStack, MultiBufferSource bufferSource, Direction face,
                                          ResourceLocation texture, int packedLight, int packedOverlay) {

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        rotateToFace(poseStack, face);

        // 偏移到面的外侧，并调整位置
        poseStack.translate(0.3, -0.5, 0.501);

        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        // 绘制一个 2x2 的四边形（-1 到 +1）
        vc.addVertex(mat, -1, -1, 0).setColor(255, 255, 255, 255)
                .setUv(0, 1).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat,  1, -1, 0).setColor(255, 255, 255, 255)
                .setUv(1, 1).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat,  1,  1, 0).setColor(255, 255, 255, 255)
                .setUv(1, 0).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, -1,  1, 0).setColor(255, 255, 255, 255)
                .setUv(0, 0).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, 0, 0, 1);

        poseStack.popPose();
    }

    /** 根据方向旋转 PoseStack，使 +Z 方向指向目标面 */
    private static void rotateToFace(PoseStack poseStack, Direction face) {
        switch (face) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case SOUTH -> { /* +Z 默认朝南，无需旋转 */ }
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            default -> {}
        }
    }
}
