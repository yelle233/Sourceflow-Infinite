package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 无限核心物品（1.20.1 Forge v2.0 版本）。
 * <p>
 * 与 1.21.1 版本保持功能对等：
 * <ul>
 *   <li>支持 L1–L4 等级与超频（OC）标志，通过构造函数传入</li>
 *   <li>使用 NBT Tag 替代 Data Component 存储绑定数据</li>
 *   <li>支持流体绑定与 Mekanism 四种化学品（Gas/InfuseType/Pigment/Slurry）绑定</li>
 * </ul>
 */
public class InfiniteCoreItem extends Item {

    // ── NBT 键名 ──────────────────────────────────────────
    private static final String TAG_BOUND_FLUID    = "BoundFluid";
    private static final String TAG_BOUND_CHEMICAL = "BoundChemical";
    private static final String TAG_BOUND          = "Filled";
    private static final String TAG_CHEM_KIND      = "MekChemKind";

    // ── 核心等级与超频 ─────────────────────────────────────
    private final int level;
    private final boolean overclocked;

    public InfiniteCoreItem(Properties properties, int level, boolean overclocked) {
        super(properties);
        this.level = Math.max(1, Math.min(4, level));
        this.overclocked = overclocked && this.level == 4;
    }

    public int getCoreLevel()       { return level; }
    public boolean isCoreOverclocked() { return overclocked; }

    public static int getLevel(ItemStack stack) {
        if (stack.getItem() instanceof InfiniteCoreItem core) return core.getCoreLevel();
        return 1;
    }

    public static boolean isOverclocked(ItemStack stack) {
        if (stack.getItem() instanceof InfiniteCoreItem core) return core.isCoreOverclocked();
        return false;
    }

    // ── 绑定类型枚举 ──────────────────────────────────────
    public enum BindType { NONE, FLUID, CHEMICAL }

    /** Mekanism 化学品种类枚举，用于区分四种独立化学品类型 */
    public enum MekChemicalKind { GAS, INFUSION, PIGMENT, SLURRY }

    // ── 右键方块交互 ──────────────────────────────────────

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = ctx.getItemInHand();
        BlockPos pos     = ctx.getClickedPos();
        Direction face   = ctx.getClickedFace();

        // 1) 尝试从流体容器绑定
        ResourceLocation fluidId = tryGetFluidIdFromHandler(level, pos, face);
        if (fluidId != null) return tryBind(player, stack, ctx.getHand(), fluidId, BindType.FLUID, null);

        // 2) 尝试从 Mekanism 化学品储罐绑定
        if (MekanismChecker.isLoaded()) {
            MekChemicalHelper.MekChemicalBinding binding =
                    MekChemicalHelper.tryGetAnyChemicalFromHandler(level, pos, face);
            if (binding != null) {
                InteractionResult r = tryBind(player, stack, ctx.getHand(), binding.id(), BindType.CHEMICAL, binding.kind());
                if (r.consumesAction()) forceUpdateStack(player, ctx.getHand(), stack);
                return r;
            }
        }

        // 3) 尝试从液体方块绑定
        ResourceLocation worldFluidId = tryGetFluidIdFromWorld(level, pos, face);
        if (worldFluidId != null) return tryBind(player, stack, ctx.getHand(), worldFluidId, BindType.FLUID, null);

        // 潜行右键：清除绑定
        if (player.isShiftKeyDown()) {
            if (!player.isCreative() && !Modconfigs.ALLOW_UNBIND_SURVIVAL.get()) {
                player.displayClientMessage(
                        Component.translatable("tooltip.yuanliuwujin.core.unbind_disabled").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            unbindOneCore(player, stack);
            forceUpdateStack(player, ctx.getHand(), stack);
            player.displayClientMessage(
                    Component.translatable("tooltip.yuanliuwujin.core.text4").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    // ── 右键空气交互 ──────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);

        if (player.isShiftKeyDown()) {
            if (!player.isCreative() && !Modconfigs.ALLOW_UNBIND_SURVIVAL.get()) {
                player.displayClientMessage(
                        Component.translatable("tooltip.yuanliuwujin.core.unbind_disabled").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.consume(stack);
            }
            unbindOneCore(player, stack);
            forceUpdateStack(player, hand, stack);
            player.displayClientMessage(
                    Component.translatable("tooltip.yuanliuwujin.core.text4").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.consume(stack);
        }

        BlockPos fluidPos = findFluidPos(level, player);
        if (fluidPos == null) return InteractionResultHolder.pass(stack);
        ResourceLocation fluidId = resolveSourceFluidId(level.getFluidState(fluidPos));
        if (fluidId == null) return InteractionResultHolder.pass(stack);
        InteractionResult result = tryBind(player, stack, hand, fluidId, BindType.FLUID, null);
        return result == InteractionResult.CONSUME ? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
    }

    // ── 库存 Tick（模型状态维护） ────────────────────────────

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 20 != 0) return;

        boolean shouldBeFilled = false;
        ResourceLocation boundFluid = getBoundFluid(stack);
        if (boundFluid != null && !Modconfigs.isFluidBanned(boundFluid)) shouldBeFilled = true;
        if (!shouldBeFilled && getBoundChemical(stack) != null) shouldBeFilled = true;

        CompoundTag tag = stack.getOrCreateTag();
        boolean currentFilled = tag.getBoolean(TAG_BOUND);
        if (shouldBeFilled != currentFilled) tag.putBoolean(TAG_BOUND, shouldBeFilled);
    }

    // ── Tooltip ───────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int lvl = this.level;
        boolean oc = this.overclocked;
        ChatFormatting levelColor = switch (lvl) {
            case 1 -> ChatFormatting.GRAY;
            case 2 -> ChatFormatting.GREEN;
            case 3 -> ChatFormatting.AQUA;
            case 4 -> oc ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.GRAY;
        };
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.core_level", lvl + (oc ? " ★" : "")).withStyle(levelColor));

        int ratio = Modconfigs.getInfiniteRatio(lvl, oc);
        tooltip.add(Component.translatable("tooltip.yuanliuwujin.infinite_core.ratio", ratio).withStyle(ChatFormatting.DARK_GRAY));

        ResourceLocation boundFluidId = getBoundFluid(stack);
        ResourceLocation boundChemId  = getBoundChemical(stack);

        if (boundFluidId == null && boundChemId == null) {
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.unbound").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bind_hint").withStyle(ChatFormatting.DARK_GRAY));
            if (MekanismChecker.isLoaded())
                tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bind_chem_hint").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        if (boundFluidId != null) {
            Fluid fluid = BuiltInRegistries.FLUID.get(boundFluidId);
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bound",
                    fluid.getFluidType().getDescription()).withStyle(ChatFormatting.GRAY));
            if (Modconfigs.isFluidBanned(boundFluidId))
                tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.banned_by_config").withStyle(ChatFormatting.RED));
        } else {
            Component chemName = null;
            if (MekanismChecker.isLoaded()) {
                MekChemicalKind kind = getBoundChemicalKind(stack);
                chemName = MekChemicalHelper.getChemicalNameByKind(kind, boundChemId);
            }
            if (chemName == null) chemName = Component.literal(boundChemId.toString());
            tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.bound_chemical", chemName).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        tooltip.add(Component.translatable("tooltip.yuanliuwujin.core.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
        if (oc) tooltip.add(Component.translatable("tooltip.yuanliuwujin.overclock_warning").withStyle(ChatFormatting.RED));
    }

    // ── 绑定数据读写（NBT） ──────────────────────────────────

    public static BindType getBindType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return BindType.NONE;
        if (tag.contains(TAG_BOUND_FLUID)) return BindType.FLUID;
        if (tag.contains(TAG_BOUND_CHEMICAL)) return BindType.CHEMICAL;
        return BindType.NONE;
    }

    public static boolean hasValidBinding(ItemStack stack) { return getBindType(stack) != BindType.NONE; }

    @Nullable
    public static ResourceLocation getBoundFluid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BOUND_FLUID)) return null;
        return ResourceLocation.tryParse(tag.getString(TAG_BOUND_FLUID));
    }

    @Nullable
    public static ResourceLocation getBoundChemical(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BOUND_CHEMICAL)) return null;
        return ResourceLocation.tryParse(tag.getString(TAG_BOUND_CHEMICAL));
    }

    public static MekChemicalKind getBoundChemicalKind(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return MekChemicalKind.GAS;
        String s = tag.getString(TAG_CHEM_KIND);
        if (s == null || s.isEmpty()) return MekChemicalKind.GAS;
        try { return MekChemicalKind.valueOf(s); }
        catch (IllegalArgumentException e) { return MekChemicalKind.GAS; }
    }

    private static void setBoundFluid(ItemStack stack, ResourceLocation id) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_BOUND_FLUID, id.toString());
        tag.remove(TAG_BOUND_CHEMICAL);
        tag.remove(TAG_CHEM_KIND);
        setCoreModelState(stack, true);
    }

    private static void setBoundChemical(ItemStack stack, MekChemicalKind kind, ResourceLocation id) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_BOUND_CHEMICAL, id.toString());
        tag.putString(TAG_CHEM_KIND, kind.name());
        tag.remove(TAG_BOUND_FLUID);
        setCoreModelState(stack, true);
    }

    private static void clearBinding(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_BOUND_FLUID);
            tag.remove(TAG_BOUND_CHEMICAL);
            tag.remove(TAG_CHEM_KIND);
        }
        setCoreModelState(stack, false);
    }

    // ── 绑定逻辑 ──────────────────────────────────────────

    private InteractionResult tryBind(Player player, ItemStack stack, InteractionHand hand,
                                      ResourceLocation substanceId, BindType type, @Nullable MekChemicalKind kind) {
        if (type == BindType.FLUID && Modconfigs.isFluidBanned(substanceId)) {
            player.displayClientMessage(
                    Component.translatable("tooltip.fluid_banned", substanceId.toString()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        BindType currentType = getBindType(stack);
        if (currentType != BindType.NONE) {
            ResourceLocation currentId = (currentType == BindType.FLUID) ? getBoundFluid(stack) : getBoundChemical(stack);
            if (currentId != null && currentId.equals(substanceId))
                player.displayClientMessage(Component.translatable("tooltip.yuanliuwujin.core.text1").withStyle(ChatFormatting.YELLOW), true);
            else
                player.displayClientMessage(Component.translatable("tooltip.yuanliuwujin.core.text2").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        bindOneCore(player, stack, substanceId, type, kind);
        forceUpdateStack(player, hand, stack);
        String key = (type == BindType.CHEMICAL) ? "tooltip.yuanliuwujin.core.text3_chem" : "tooltip.yuanliuwujin.core.text3";
        player.displayClientMessage(
                Component.translatable(key).withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(substanceId.toString()).withStyle(ChatFormatting.AQUA)), true);
        return InteractionResult.CONSUME;
    }

    private static void bindOneCore(Player player, ItemStack stackInHand,
                                    ResourceLocation id, BindType type, @Nullable MekChemicalKind kind) {
        if (player.level().isClientSide) return;
        if (stackInHand.getCount() == 1) {
            applyBinding(stackInHand, id, type, kind);
            setCoreModelState(stackInHand, true);
            syncInventory(player);
            return;
        }
        stackInHand.shrink(1);
        ItemStack single = stackInHand.copy();
        single.setCount(1);
        applyBinding(single, id, type, kind);
        setCoreModelState(single, true);
        if (!player.addItem(single)) player.drop(single, false);
        syncInventory(player);
    }

    private static void applyBinding(ItemStack stack, ResourceLocation id, BindType type, @Nullable MekChemicalKind kind) {
        if (type == BindType.CHEMICAL && kind != null) setBoundChemical(stack, kind, id);
        else setBoundFluid(stack, id);
    }

    private static void unbindOneCore(Player player, ItemStack stackInHand) {
        if (stackInHand.getCount() == 1) { clearBinding(stackInHand); return; }
        stackInHand.shrink(1);
        ItemStack single = stackInHand.copy();
        single.setCount(1);
        clearBinding(single);
        if (!player.addItem(single)) player.drop(single, false);
    }

    private static void setCoreModelState(ItemStack stack, boolean filled) {
        stack.getOrCreateTag().putBoolean(TAG_BOUND, filled);
    }

    // ── 辅助方法 ──────────────────────────────────────────

    private static void forceUpdateStack(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, stack);
        player.getInventory().setChanged();
        if (!player.level().isClientSide) player.inventoryMenu.broadcastChanges();
    }

    private static void syncInventory(Player player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    @Nullable
    private static ResourceLocation tryGetFluidIdFromWorld(Level level, BlockPos pos, Direction clickedFace) {
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.isEmpty()) fluidState = level.getFluidState(pos.relative(clickedFace));
        return fluidState.isEmpty() ? null : resolveSourceFluidId(fluidState);
    }

    @Nullable
    private static ResourceLocation resolveSourceFluidId(FluidState fluidState) {
        if (fluidState.isEmpty()) return null;
        Fluid fluid = fluidState.getType();
        if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    @Nullable
    private static BlockPos findFluidPos(Level level, Player player) {
        double reach = 4.5;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        double step = 0.1;
        int steps = (int) Math.ceil(reach / step);
        BlockPos lastPos = null;
        for (int i = 0; i <= steps; i++) {
            Vec3 point = eye.add(look.scale(i * step));
            BlockPos pos = BlockPos.containing(point);
            if (pos.equals(lastPos)) continue;
            lastPos = pos;
            if (!level.getFluidState(pos).isEmpty()) return pos;
        }
        return null;
    }

    @Nullable
    private static ResourceLocation tryGetFluidIdFromHandler(Level level, BlockPos pos, @Nullable Direction preferredSide) {
        var be = level.getBlockEntity(pos);
        if (be == null) return null;
        if (preferredSide != null) {
            ResourceLocation id = be.getCapability(ForgeCapabilities.FLUID_HANDLER, preferredSide)
                    .map(InfiniteCoreItem::firstNonEmptyFluidId).orElse(null);
            if (id != null) return id;
        }
        for (Direction d : Direction.values()) {
            ResourceLocation id = be.getCapability(ForgeCapabilities.FLUID_HANDLER, d)
                    .map(InfiniteCoreItem::firstNonEmptyFluidId).orElse(null);
            if (id != null) return id;
        }
        try {
            return be.getCapability(ForgeCapabilities.FLUID_HANDLER, null)
                    .map(InfiniteCoreItem::firstNonEmptyFluidId).orElse(null);
        } catch (Throwable ignored) { return null; }
    }

    @Nullable
    private static ResourceLocation firstNonEmptyFluidId(@Nullable IFluidHandler handler) {
        if (handler == null) return null;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fs = handler.getFluidInTank(i);
            if (fs == null || fs.isEmpty()) continue;
            Fluid fluid = fs.getFluid();
            if (fluid instanceof FlowingFluid ff) fluid = ff.getSource();
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id != null) return id;
        }
        return null;
    }
}

