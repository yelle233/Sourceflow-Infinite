package com.yelle233.yuanliuwujin.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public class InfiniteCoreItem extends Item {

    private static final String TAG_BOUND_FLUID = "BoundFluidId";

    public InfiniteCoreItem(Properties properties) {
        super(properties);
    }

    // 右键方块：如果点到的是液体方块，就绑定
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();

        // 潜行右键：清除绑定
        if (player.isShiftKeyDown()) {
            unbindOneCore(player, stack);
            player.displayClientMessage(Component.literal("已清除绑定").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }

        // 首先检查点击位置本身
        FluidState fluidState = level.getFluidState(pos);

        // 如果点击位置没有液体，检查点击面的对侧（液体可能在那里）
        if (fluidState.isEmpty()) {
            Direction face = ctx.getClickedFace();
            BlockPos fluidPos = pos.relative(face);
            fluidState = level.getFluidState(fluidPos);

            // 如果对侧也没有液体，返回 PASS 让 use 方法处理射线检测
            if (fluidState.isEmpty()) {
                return InteractionResult.PASS;
            }
        }

        Fluid fluid = fluidState.getType();

        if (fluid instanceof FlowingFluid ff) {
            fluid = ff.getSource();
        }

        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);

        ResourceLocation bound = getBoundFluid(stack);
        if (bound != null) {
            if (bound.equals(id)) {
                player.displayClientMessage(Component.literal("已绑定该液体").withStyle(ChatFormatting.YELLOW), true);
            } else {
                player.displayClientMessage(Component.literal("已绑定液体，潜行右键清除后才能重新绑定").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.CONSUME;
        }

        bindOneCore(player, stack, id);

        // 提示玩家
        player.displayClientMessage(
                Component.literal("已绑定：").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(id.toString()).withStyle(ChatFormatting.AQUA)),
                true
        );

        return InteractionResult.CONSUME;
    }

    // 空中右键：潜行清除（有些玩家喜欢不用点方块也能清）
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 潜行空中右键：清除绑定
        if (!level.isClientSide && player.isShiftKeyDown()) {
            unbindOneCore(player, stack);
            player.displayClientMessage(Component.literal("已清除绑定").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.consume(stack);
        }

        // 空中右键：射线检测玩家准星指向（包含液体）
        if (!level.isClientSide) {
            BlockPos fluidPos = findFluidPos(level, player);
            if (fluidPos != null) {
                FluidState fluidState = level.getFluidState(fluidPos);
                Fluid fluid = fluidState.getType();
                if (fluid instanceof FlowingFluid ff) {
                    fluid = ff.getSource();
                }

                ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);

                ResourceLocation bound = getBoundFluid(stack);
                if (bound != null) {
                    if (bound.equals(id)) {
                        player.displayClientMessage(Component.literal("已绑定该液体").withStyle(ChatFormatting.YELLOW), true);
                    } else {
                        player.displayClientMessage(Component.literal("已绑定液体，潜行右键清除后才能重新绑定").withStyle(ChatFormatting.RED), true);
                    }
                    return InteractionResultHolder.consume(stack);
                }

                bindOneCore(player, stack, id);


                player.displayClientMessage(
                        Component.literal("已绑定：").withStyle(ChatFormatting.GREEN)
                                .append(Component.literal(id.toString()).withStyle(ChatFormatting.AQUA)),
                        true
                );

                return InteractionResultHolder.consume(stack);
            }
        }


        return InteractionResultHolder.pass(stack);
    }

    @Nullable
    private static BlockPos findFluidPos(Level level, Player player) {
        double reach = player.blockInteractionRange(); // 交互距离
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();

        // 步长越小越稳，但越耗；0.1 基本很稳且开销可接受
        double step = 0.1;
        int steps = (int) Math.ceil(reach / step);

        BlockPos lastPos = null;

        for (int i = 0; i <= steps; i++) {
            double dist = i * step;
            Vec3 p = eye.add(look.x * dist, look.y * dist, look.z * dist);
            BlockPos pos = BlockPos.containing(p);

            // 防止同一格重复检查
            if (lastPos != null && lastPos.equals(pos)) continue;
            lastPos = pos;

            if (!level.getFluidState(pos).isEmpty()) {
                return pos;
            }
        }

        return null;
    }


    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation bound = getBoundFluid(stack);
        if (bound == null) {
            tooltip.add(Component.literal("未绑定液体").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("右键液体方块进行绑定").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("潜行右键清除绑定").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("已绑定：").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(bound.toString()).withStyle(ChatFormatting.AQUA)));
            tooltip.add(Component.literal("潜行右键清除绑定").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void setCoreModelState(ItemStack stack, boolean filled) {
        if (filled) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        } else {
            // 你可以选择设为 0 或直接 remove
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }

    private static void bindOneCore(Player player, ItemStack stackInHand, ResourceLocation fluidId) {
        // 手上只有 1 个：直接绑定
        if (stackInHand.getCount() == 1) {
            setBoundFluid(stackInHand, fluidId);
            setCoreModelState(stackInHand, true);
            return;
        }

        // 手上 >1：拆分出 1 个进行绑定
        stackInHand.shrink(1);

        ItemStack single = stackInHand.copy();
        single.setCount(1);

        setBoundFluid(single, fluidId);
        setCoreModelState(single, true);

        // 放回背包，满了就丢地上
        if (!player.addItem(single)) {
            player.drop(single, false);
        }
    }

    private static void unbindOneCore(Player player, ItemStack stackInHand) {
        // 手上只有 1 个：直接清除
        if (stackInHand.getCount() == 1) {
            clearBoundFluid(stackInHand);
            setCoreModelState(stackInHand, false);
            return;
        }

        // 手上 >1：拆分出 1 个进行清除
        stackInHand.shrink(1);

        ItemStack single = stackInHand.copy();
        single.setCount(1);

        clearBoundFluid(single);
        setCoreModelState(single, false);

        // 放回背包，满了就丢地上
        if (!player.addItem(single)) {
            player.drop(single, false);
        }
    }


    // ====== NBT helpers ======
    private static void setBoundFluid(ItemStack stack, ResourceLocation id) {
        CompoundTag tag = getOrCreateCustomTag(stack);
        tag.putString(TAG_BOUND_FLUID, id.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearBoundFluid(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;

        CompoundTag tag = data.copyTag();
        tag.remove(TAG_BOUND_FLUID);

        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Nullable
    public static ResourceLocation getBoundFluid(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;

        CompoundTag tag = data.getUnsafe();
        if (!tag.contains(TAG_BOUND_FLUID)) return null;

        return ResourceLocation.tryParse(tag.getString(TAG_BOUND_FLUID));
    }

    private static CompoundTag getOrCreateCustomTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }
}
