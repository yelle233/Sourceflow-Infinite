package com.yelle233.yuanliuwujin.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.blockentity.VoidGeneratorBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 虚空发电机的方块实体渲染器（BER）
 *
 * 渲染各面的输出状态覆盖层（开启/关闭）
 */
public class VoidGeneratorBER implements BlockEntityRenderer<VoidGeneratorBlockEntity> {

    /** 输出开启的面贴图 */
    private static final ResourceLocation OVERLAY_OUTPUT =
            new ResourceLocation(SourceflowInfinite.MODID, "textures/block/overlay_on.png");

    public VoidGeneratorBER(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(VoidGeneratorBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // 渲染各面的输出状态覆盖层（跳过底面）
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue; // 底面是输入，不渲染

            boolean outputEnabled = be.getSideOutput(dir);
            if (!outputEnabled) continue; // 关闭的面不渲染

            renderFaceOverlay(poseStack, bufferSource, dir, OVERLAY_OUTPUT, LightTexture.FULL_BRIGHT, packedOverlay);
        }
    }

    /**
     * 在指定面上渲染一张半透明的输出指示贴图
     */
    private static void renderFaceOverlay(PoseStack poseStack, MultiBufferSource bufferSource, Direction face,
                                          ResourceLocation texture, int packedLight, int packedOverlay) {

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        rotateToFace(poseStack, face);

        // 偏移到面的外侧
        poseStack.translate(0.3, -0.5, 0.501);

        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        // 绘制一个 2x2 的四边形
        vc.vertex(mat, -1, -1, 0).color(255, 255, 255, 255)
                .uv(0, 1).overlayCoords(packedOverlay).uv2(packedLight).normal(pose.normal(), 0, 0, 1).endVertex();
        vc.vertex(mat,  1, -1, 0).color(255, 255, 255, 255)
                .uv(1, 1).overlayCoords(packedOverlay).uv2(packedLight).normal(pose.normal(), 0, 0, 1).endVertex();
        vc.vertex(mat,  1,  1, 0).color(255, 255, 255, 255)
                .uv(1, 0).overlayCoords(packedOverlay).uv2(packedLight).normal(pose.normal(), 0, 0, 1).endVertex();
        vc.vertex(mat, -1,  1, 0).color(255, 255, 255, 255)
                .uv(0, 0).overlayCoords(packedOverlay).uv2(packedLight).normal(pose.normal(), 0, 0, 1).endVertex();

        poseStack.popPose();
    }

    /** 根据方向旋转 PoseStack */
    private static void rotateToFace(PoseStack poseStack, Direction face) {
        switch (face) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case SOUTH -> { /* +Z 默认朝南，无需旋转 */ }
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            case UP    -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
        }
    }
}
