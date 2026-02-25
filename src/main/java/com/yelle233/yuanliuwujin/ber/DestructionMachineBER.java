package com.yelle233.yuanliuwujin.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.blockentity.DestructionMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.DestructionMachineBlockEntity.SideMode;
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
 * 销毁机器的方块实体渲染器（BER）。
 * <p>
 * 渲染两部分内容：
 * <ol>
 *   <li>机器中心反向旋转的销毁核心装饰方块（插入核心时显示）</li>
 *   <li>各激活面的模式覆盖层贴图（BOTH / PUSH）</li>
 * </ol>
 * <p>
 */
public class DestructionMachineBER implements BlockEntityRenderer<DestructionMachineBlockEntity> {

    /** BOTH 模式的面贴图 */
    private static final ResourceLocation OVERLAY_BOTH =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID,
                    "textures/block/overlay_both.png");

    /** PULL 模式的面贴图 */
    private static final ResourceLocation OVERLAY_PUSH =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID,
                    "textures/block/overlay_push.png");

    public DestructionMachineBER(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(DestructionMachineBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // 1) 渲染内部旋转的销毁核心
        renderCoreInside(be, partialTick, poseStack, bufferSource);

        // 2) 渲染各面的模式覆盖层（跳过顶面、底面和 OFF 模式）
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            SideMode mode = be.getSideMode(dir);
            if (mode == SideMode.OFF) continue;

            ResourceLocation tex = (mode == SideMode.BOTH) ? OVERLAY_BOTH : OVERLAY_PUSH;
            renderFaceOverlay(poseStack, bufferSource, dir, tex,
                    LightTexture.FULL_BRIGHT, packedOverlay);
        }
    }

    /* ====== 内部核心渲染 ====== */

    /**
     * 渲染销毁核心装饰方块，使用反向旋转营造"吸入"的视觉效果。
     */
    private static void renderCoreInside(DestructionMachineBlockEntity be, float partialTick,
                                          PoseStack poseStack, MultiBufferSource bufferSource) {
        Level level = be.getLevel();
        if (level == null) return;
        if (be.getCoreSlot().getStackInSlot(0).isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        float time = level.getGameTime() + partialTick;
        // 与无限流体机器相反的旋转方向，视觉上表示"吸入"
        poseStack.mulPose(Axis.YP.rotationDegrees(-(time * 2.0f) % 360.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-(time * 1.5f) % 360.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-(time * 1.0f) % 360.0f));
        poseStack.scale(1.5f, 1.5f, 1.5f);

        // 使用销毁核心装饰方块的默认 BlockState 渲染
        BlockState coreState = ModBlocks.DESTRUCTION_CORE_BLOCK.get().defaultBlockState();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                coreState, poseStack, bufferSource,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }

    /* ====== 面覆盖层渲染（与 InfiniteFluidMachineBER 逻辑相同） ====== */

    private static void renderFaceOverlay(PoseStack poseStack, MultiBufferSource bufferSource,
                                           Direction face, ResourceLocation texture,
                                           int packedLight, int packedOverlay) {
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
        int fullBright = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;


        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        rotateToFace(poseStack, face);
        poseStack.translate(0.3, -0.5, 0.501); // 偏移到面外侧避免 Z-fighting

        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        // 绘制覆盖面（2x2 四边形）
        vc.addVertex(mat, -1, -1, 0).setColor(255, 255, 255, 255)
                .setUv(0, 1).setOverlay(packedOverlay).setLight(fullBright).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat,  1, -1, 0).setColor(255, 255, 255, 255)
                .setUv(1, 1).setOverlay(packedOverlay).setLight(fullBright).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat,  1,  1, 0).setColor(255, 255, 255, 255)
                .setUv(1, 0).setOverlay(packedOverlay).setLight(fullBright).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, -1,  1, 0).setColor(255, 255, 255, 255)
                .setUv(0, 0).setOverlay(packedOverlay).setLight(fullBright).setNormal(pose, 0, 0, 1);

        poseStack.popPose();
    }

    private static void rotateToFace(PoseStack poseStack, Direction face) {
        switch (face) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case SOUTH -> { /* 默认朝南，无需旋转 */ }
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            default    -> {}
        }
    }
}
