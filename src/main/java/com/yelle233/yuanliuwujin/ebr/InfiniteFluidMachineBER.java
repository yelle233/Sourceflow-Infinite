package com.yelle233.yuanliuwujin.ebr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class InfiniteFluidMachineBER implements BlockEntityRenderer<InfiniteFluidMachineBlockEntity> {

    private static final ResourceLocation OVERLAY_PULL =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "textures/block/overlay_pull.png");
    private static final ResourceLocation OVERLAY_BOTH =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "textures/block/overlay_both.png");

    public InfiniteFluidMachineBER(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(InfiniteFluidMachineBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {


        renderCoreBlockInside(be, partialTick, poseStack, bufferSource, packedLight);


        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;

            InfiniteFluidMachineBlockEntity.SideMode mode = be.getSideMode(dir);

            //跳过OFF
            if (mode == InfiniteFluidMachineBlockEntity.SideMode.OFF) continue;

            ResourceLocation tex = (mode == InfiniteFluidMachineBlockEntity.SideMode.PULL)
                    ? OVERLAY_PULL
                    : OVERLAY_BOTH;

            int fullBright = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            renderFaceOverlay(poseStack, bufferSource, dir, tex, fullBright, packedOverlay);
        }
    }


    private static void renderCoreBlockInside(InfiniteFluidMachineBlockEntity be, float partialTick,
                                              PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        if (be.getLevel() == null) return;

        // 没有装配核心不渲染
        if (be.getCoreSlot().getStackInSlot(0).isEmpty()) return;


        Level level = be.getLevel();

        poseStack.pushPose();

        poseStack.translate(0.5, 0.5, 0.5);

        // 旋转
        float t = level.getGameTime() + partialTick;
        float rotY = (t * 2.0f) % 360.0f;
        poseStack.mulPose(Axis.YP.rotationDegrees(rotY));

        // 倾斜角度
        poseStack.mulPose(Axis.XP.rotationDegrees(0.0f));


        // 缩放
        float scale = 2.0f;
        poseStack.scale(scale, scale, scale);


        // 渲染模型
        BlockState coreState = ModBlocks.INFINITE_CORE_BLOCK.get().defaultBlockState();

        // 亮度
        int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                coreState,
                poseStack,
                bufferSource,
                light,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }

    private static void renderFaceOverlay(PoseStack poseStack, MultiBufferSource bufferSource, Direction face,
                                          ResourceLocation texture, int packedLight, int packedOverlay) {

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));

        poseStack.pushPose();

        poseStack.translate(0.5, 0.5, 0.5);


        rotateToFace(poseStack, face);


        double z = 0.501;
        poseStack.translate(0.3, -0.5, z);

        float s = 1.0f;
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        vc.addVertex(mat, -s, -s, 0)
                .setColor(255, 255, 255, 255)
                .setUv(0, 1)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, 1);
        vc.addVertex(mat,  s, -s, 0)
                .setColor(255, 255, 255, 255)
                .setUv(1, 1)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, 1);
        vc.addVertex(mat,  s,  s, 0)
                .setColor(255, 255, 255, 255)
                .setUv(1, 0)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, -s,  s, 0)
                .setColor(255, 255, 255, 255)
                .setUv(0, 0)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, 1);

        poseStack.popPose();
    }

    private static void rotateToFace(PoseStack poseStack, Direction face) {
        switch (face) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case SOUTH -> { /* +Z 默认就是 SOUTH */ }
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            default -> {}
        }
    }

}
