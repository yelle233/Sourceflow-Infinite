package com.yelle233.yuanliuwujin.ber;

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
 * 无限流体机器的方块实体渲染器（1.20.1 Forge 版本）。
 */
public class InfiniteFluidMachineBER implements BlockEntityRenderer<InfiniteFluidMachineBlockEntity> {

    private static final ResourceLocation OVERLAY_PULL =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "textures/block/overlay_pull.png");
    private static final ResourceLocation OVERLAY_BOTH =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "textures/block/overlay_both.png");

    public InfiniteFluidMachineBER(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(InfiniteFluidMachineBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        renderCoreBlockInside(be, partialTick, poseStack, bufferSource);

        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            SideMode mode = be.getSideMode(dir);
            if (mode == SideMode.OFF) continue;
            ResourceLocation tex = (mode == SideMode.PULL) ? OVERLAY_PULL : OVERLAY_BOTH;
            renderFaceOverlay(poseStack, bufferSource, dir, tex, LightTexture.FULL_BRIGHT, packedOverlay);
        }
    }

    private static void renderCoreBlockInside(InfiniteFluidMachineBlockEntity be, float partialTick,
                                              PoseStack poseStack, MultiBufferSource bufferSource) {
        Level level = be.getLevel();
        if (level == null) return;
        if (be.getCoreSlot().getStackInSlot(0).isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        float time1 = level.getGameTime() + partialTick;
        poseStack.mulPose(Axis.YP.rotationDegrees((time1 * 2.0f) % 360.0f));
        float time2 = level.getGameTime() + partialTick;
        poseStack.mulPose(Axis.XP.rotationDegrees((time2 * 2.0f) % 360.0f));
        float time3 = level.getGameTime() + partialTick;
        poseStack.mulPose(Axis.ZP.rotationDegrees((time3 * 2.0f) % 360.0f));

        poseStack.scale(2.0f, 2.0f, 2.0f);

        BlockState coreState = ModBlocks.INFINITE_CORE_BLOCK.get().defaultBlockState();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                coreState, poseStack, bufferSource,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }

    private static void renderFaceOverlay(PoseStack poseStack, MultiBufferSource bufferSource, Direction face,
                                          ResourceLocation texture, int packedLight, int packedOverlay) {

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        rotateToFace(poseStack, face);

        poseStack.translate(0.3, -0.5, 0.501);

        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

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

    private static void rotateToFace(PoseStack poseStack, Direction face) {
        switch (face) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case SOUTH -> { }
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            default -> {}
        }
    }
}
