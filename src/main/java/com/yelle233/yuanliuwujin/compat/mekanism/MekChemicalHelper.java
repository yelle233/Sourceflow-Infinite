package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;

import javax.annotation.Nullable;

/**
 * Mekanism 化学品集成帮助类。
 * <p>
 * <b>重要</b>：此类引用了 Mekanism API 类，只能在确认 Mekanism 已加载后调用！
 * 请始终通过 {@code MekanismChecker.isLoaded()} 判断后再调用此类的方法。
 * <p>
 * 通过 NeoForge 的 BlockCapability 系统与 Mekanism 的化学品储罐交互。
 */
public final class MekChemicalHelper {

    private MekChemicalHelper() {}

    /**
     * 对 Mekanism 化学品 BlockCapability 的引用。
     * <p>
     * NeoForge 的 BlockCapability 以 ResourceLocation 为 key 去重，
     * 因此此引用与 Mekanism 内部注册的 capability 是同一个实例。
     * <p>
     * 注意：javax.annotation.Nullable 不支持 TYPE_USE，所以不在泛型参数上标注。
     * BlockCapability.createSided() 已经隐含 context 可为 null 的语义。
     */
    public static final BlockCapability<IChemicalHandler, Direction> CHEMICAL_HANDLER_CAP =
            BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"),
                    IChemicalHandler.class
            );

    /* ====== 从方块读取化学品 ====== */

    /**
     * 尝试从指定位置方块的化学品 Handler 中获取第一个非空化学品的 ID。
     *
     * @param level         世界
     * @param pos           方块位置
     * @param preferredSide 优先检查的面（玩家点击面）
     * @return 化学品的 ResourceLocation，未找到返回 null
     */
    @Nullable
    public static ResourceLocation tryGetChemicalIdFromHandler(Level level, BlockPos pos,
                                                                @Nullable Direction preferredSide) {
        // 1) 优先使用指定面
        if (preferredSide != null) {
            ResourceLocation id = firstNonEmptyChemicalId(
                    level.getCapability(CHEMICAL_HANDLER_CAP, pos, preferredSide));
            if (id != null) return id;
        }

        // 2) 遍历所有面
        for (Direction d : Direction.values()) {
            ResourceLocation id = firstNonEmptyChemicalId(
                    level.getCapability(CHEMICAL_HANDLER_CAP, pos, d));
            if (id != null) return id;
        }

        // 3) 尝试 null context（某些方块可能不区分面）
        try {
            return firstNonEmptyChemicalId(level.getCapability(CHEMICAL_HANDLER_CAP, pos, null));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 从 IChemicalHandler 的所有槽中找到第一个非空化学品的注册 ID */
    @Nullable
    private static ResourceLocation firstNonEmptyChemicalId(@Nullable IChemicalHandler handler) {
        if (handler == null) return null;
        for (int i = 0; i < handler.getChemicalTanks(); i++) {
            ChemicalStack stack = handler.getChemicalInTank(i);
            if (stack.isEmpty()) continue;
            return MekanismAPI.CHEMICAL_REGISTRY.getKey(stack.getChemical());
        }
        return null;
    }

    /* ====== 化学品查找与信息 ====== */

    /**
     * 通过 ResourceLocation 获取 Chemical 对象。
     *
     * @return Chemical 对象，未找到则返回 null
     */
    @Nullable
    public static Chemical getChemical(@Nullable ResourceLocation id) {
        if (id == null) return null;
        Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(id);
        // 10.7.11+ isEmptyType() 已弃用，改用 getStack(1).isEmpty() 检查化学品是否为空类型
        if (chemical == null || chemical.getStack(1).isEmpty()) return null;
        return chemical;
    }

    /**
     * 获取化学品的显示名称。
     */
    @Nullable
    public static Component getChemicalName(@Nullable ResourceLocation id) {
        Chemical chemical = getChemical(id);
        return chemical != null ? chemical.getTextComponent() : null;
    }

    /* ====== 化学品推送（BOTH 模式主动输出） ====== */

    /**
     * 向相邻方块的化学品 Handler 推送化学品。
     *
     * @param level  世界
     * @param pos    机器位置
     * @param dir    推送方向
     * @param chemId 化学品 ID
     * @param amount 推送量（mB）
     */
    public static void pushChemical(Level level, BlockPos pos, Direction dir,
                                     ResourceLocation chemId, long amount) {
        Chemical chemical = getChemical(chemId);
        if (chemical == null || amount <= 0) return;

        BlockPos neighborPos = pos.relative(dir);
        IChemicalHandler handler = level.getCapability(CHEMICAL_HANDLER_CAP, neighborPos, dir.getOpposite());
        if (handler == null) return;

        // 10.7.11+: 使用 Chemical.getStack(long) 替代已弃用的 new ChemicalStack(Chemical, long)
        ChemicalStack toInsert = chemical.getStack(amount);

        // insertChemical 返回未被接受的剩余量（remainder）
        ChemicalStack remainder = handler.insertChemical(toInsert, Action.SIMULATE);
        if (remainder.isEmpty() || remainder.getAmount() < amount) {
            handler.insertChemical(chemical.getStack(amount), Action.EXECUTE);
        }
    }
}
