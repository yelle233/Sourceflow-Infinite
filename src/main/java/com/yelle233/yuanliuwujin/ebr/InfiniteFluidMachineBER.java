package com.yelle233.yuanliuwujin.ebr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class InfiniteFluidMachineBER implements BlockEntityRenderer<InfiniteFluidMachineBlockEntity> {
    private static final ResourceLocation OVERLAY_OFF  =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "textures/block/overlay_off.png");
    private static final ResourceLocation OVERLAY_PULL =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "textures/block/overlay_pull.png");
    private static final ResourceLocation OVERLAY_BOTH =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "textures/block/overlay_both.png");

    public InfiniteFluidMachineBER(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(InfiniteFluidMachineBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // 没核心/没绑定液体也可以显示模式（看你喜好），这里我不做限制
        // if (be.getBoundSourceFluid() == null) return;

        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue; // 你说顶面不参与
            // 你也可以选择 DOWN 不显示，按你喜好

            InfiniteFluidMachineBlockEntity.SideMode mode = be.getSideMode(dir);
            if (mode == InfiniteFluidMachineBlockEntity.SideMode.OFF) {
                // OFF 也显示贴图（比如灰色），所以不 continue
            }

            ResourceLocation tex = switch (mode) {
                case OFF -> OVERLAY_OFF;
                case PULL -> OVERLAY_PULL;
                case BOTH -> OVERLAY_BOTH;
            };

            int fullBright = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

            renderFaceOverlay(poseStack, bufferSource, dir, tex, fullBright, packedOverlay);
        }
    }

    private static void renderFaceOverlay(PoseStack poseStack, MultiBufferSource bufferSource, Direction face,
                                          ResourceLocation texture, int packedLight, int packedOverlay) {

        // 渲染一张贴图的“薄片”贴在方块表面上
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));

        poseStack.pushPose();

        // 移动到方块中心
        poseStack.translate(0.5, 0.5, 0.5);

        // 朝向指定面
        rotateToFace(poseStack, face);

        // 贴到表面上（稍微抬高一点避免 Z-fighting）
        // 当前坐标系：面朝向 +Z
        double z = 0.501; // 0.5 是表面，0.501 稍微浮起来
        poseStack.translate(0, 0, z);

        // overlay 大小：建议比整面小一些，像 Create 那样有边距
        float s = 0.45f; // 0.5 是整面半径，这里留点边
        // 画一个以中心为原点的正方形
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
//        Matrix3f normal = pose.normal();

        // 4 个点（逆时针）
        // UV 按整张图 [0..1]
        vc.addVertex(mat, -s, -s, 0)
                .setColor(255,255,255,255)
                .setUv(0,1)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0,0,1);
        vc.addVertex(mat, s, -s, 0)
                .setColor(255, 255, 255, 255)
                .setUv(1.0f, 1.0f)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0.0f, 0.0f, 1.0f);
        vc.addVertex(mat,  s,  s, 0)
                .setColor(255,255,255,255)
                .setUv(1,0)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0,0,1);
        vc.addVertex(mat, -s,  s, 0)
                .setColor(255,255,255,255)
                .setUv(0,0)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0,0,1);

        poseStack.popPose();
    }

    private static void rotateToFace(PoseStack poseStack, Direction face) {
        // 让当前平面朝向 face 的外侧。我们定义“贴图默认朝 +Z”
        // 所以要把 +Z 旋到目标面方向
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
