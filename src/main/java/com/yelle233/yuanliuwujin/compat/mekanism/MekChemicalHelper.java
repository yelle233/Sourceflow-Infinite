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
 */
public final class MekChemicalHelper {

    private MekChemicalHelper() {}

    public static final BlockCapability<IChemicalHandler, Direction> CHEMICAL_HANDLER_CAP =
            BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"),
                    IChemicalHandler.class
            );

    /* ====== 从方块读取化学品 ====== */

    @Nullable
    public static ResourceLocation tryGetChemicalIdFromHandler(Level level, BlockPos pos,
                                                                @Nullable Direction preferredSide) {
        if (preferredSide != null) {
            ResourceLocation id = firstNonEmptyChemicalId(
                    level.getCapability(CHEMICAL_HANDLER_CAP, pos, preferredSide));
            if (id != null) return id;
        }
        for (Direction d : Direction.values()) {
            ResourceLocation id = firstNonEmptyChemicalId(
                    level.getCapability(CHEMICAL_HANDLER_CAP, pos, d));
            if (id != null) return id;
        }
        try {
            return firstNonEmptyChemicalId(level.getCapability(CHEMICAL_HANDLER_CAP, pos, null));
        } catch (Throwable ignored) {
            return null;
        }
    }

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

    @Nullable
    public static Chemical getChemical(@Nullable ResourceLocation id) {
        if (id == null) return null;
        Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(id);
        if (chemical == null || chemical.getStack(1).isEmpty()) return null;
        return chemical;
    }

    @Nullable
    public static Component getChemicalName(@Nullable ResourceLocation id) {
        Chemical chemical = getChemical(id);
        return chemical != null ? chemical.getTextComponent() : null;
    }

    /* ====== 化学品推送（无限流体机器 BOTH 模式主动输出） ====== */

    public static void pushChemical(Level level, BlockPos pos, Direction dir,
                                     ResourceLocation chemId, long amount) {
        pushChemicalWithLimit(level, pos, dir, chemId, amount);
    }

    /**
     * 向相邻方块推送化学品，返回实际推送量。
     */
    public static long pushChemicalWithLimit(Level level, BlockPos pos, Direction dir,
                                              ResourceLocation chemId, long amount) {
        Chemical chemical = getChemical(chemId);
        if (chemical == null || amount <= 0) return 0;

        BlockPos neighborPos = pos.relative(dir);
        IChemicalHandler handler = level.getCapability(CHEMICAL_HANDLER_CAP, neighborPos, dir.getOpposite());
        if (handler == null) return 0;

        ChemicalStack toInsert = chemical.getStack(amount);
        ChemicalStack remainder = handler.insertChemical(toInsert, Action.EXECUTE);
        return amount - (remainder.isEmpty() ? 0 : remainder.getAmount());
    }

    /* ====== 化学品抽取并销毁（销毁机器 PULL 模式主动抽取） ====== */

    /**
     * 从相邻方块的化学品 Handler 中主动抽取化学品并将其虚空销毁。
     * <p>
     * 供销毁机器的 PULL 模式 serverTick 使用：先模拟后执行，
     * 将抽取到的化学品直接丢弃（不存储），实现虚空销毁效果。
     *
     * @param level  世界
     * @param pos    销毁机器的位置
     * @param dir    抽取方向（机器面向相邻方块的方向）
     * @param amount 本次最多抽取量（受每 tick 预算限制）
     * @return 实际销毁的化学品量（mB），用于扣减调用方的预算
     */
    public static long drainAndDestroyChemical(Level level, BlockPos pos,
                                                Direction dir, long amount) {
        if (amount <= 0) return 0;

        BlockPos neighborPos = pos.relative(dir);
        // 从相邻方块的反方向面获取 Handler（管道从该面暴露 capability）
        IChemicalHandler handler = level.getCapability(
                CHEMICAL_HANDLER_CAP, neighborPos, dir.getOpposite());
        if (handler == null) return 0;

        // 先模拟，确认可抽取量
        ChemicalStack simResult = handler.extractChemical(amount, Action.SIMULATE);
        if (simResult.isEmpty()) return 0;

        // 实际抽取（化学品被取走后直接丢弃 = 虚空销毁）
        ChemicalStack extracted = handler.extractChemical(simResult.getAmount(), Action.EXECUTE);
        return extracted.isEmpty() ? 0 : extracted.getAmount();
    }
}
