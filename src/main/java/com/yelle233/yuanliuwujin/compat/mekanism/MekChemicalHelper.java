package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.MekChemicalKind;

import javax.annotation.Nullable;

/**
 * Mekanism 化学品（Gas）集成帮助类（1.20.1 Forge 版本）。
 * <p>
 * <b>重大变化</b>：Mekanism 10.4.x（1.20.1）使用分离的化学品类型系统
 * （Gas / InfuseType / Pigment / Slurry），而非后续版本的统一 Chemical。
 * <p>
 * <b>重要</b>：此类引用了 Mekanism API 类，只能在确认 Mekanism 已加载后调用！
 */
public final class MekChemicalHelper {

    private MekChemicalHelper() {}

    public static final Capability<IGasHandler> GAS_HANDLER_CAP = Capabilities.GAS_HANDLER;
    public static final Capability<IInfusionHandler> INFUSION_HANDLER_CAP = Capabilities.INFUSION_HANDLER;
    public static final Capability<IPigmentHandler> PIGMENT_HANDLER_CAP = Capabilities.PIGMENT_HANDLER;
    public static final Capability<ISlurryHandler> SLURRY_HANDLER_CAP = Capabilities.SLURRY_HANDLER;

    /* ====== 从方块读取化学品 ====== */

    @Nullable
    public static ResourceLocation tryGetChemicalIdFromHandler(Level level, BlockPos pos,
                                                                @Nullable Direction preferredSide) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        if (preferredSide != null) {
            ResourceLocation id = be.getCapability(GAS_HANDLER_CAP, preferredSide)
                    .map(MekChemicalHelper::firstNonEmptyGasId).orElse(null);
            if (id != null) return id;
        }
        for (Direction d : Direction.values()) {
            ResourceLocation id = be.getCapability(GAS_HANDLER_CAP, d)
                    .map(MekChemicalHelper::firstNonEmptyGasId).orElse(null);
            if (id != null) return id;
        }
        try {
            return be.getCapability(GAS_HANDLER_CAP, null)
                    .map(MekChemicalHelper::firstNonEmptyGasId).orElse(null);
        } catch (Throwable ignored) { return null; }
    }

    @Nullable
    private static ResourceLocation firstNonEmptyGasId(@Nullable IGasHandler handler) {
        if (handler == null) return null;
        for (int i = 0; i < handler.getTanks(); i++) {
            GasStack stack = handler.getChemicalInTank(i);
            if (stack.isEmpty()) continue;
            return MekanismAPI.gasRegistry().getKey(stack.getType());
        }
        return null;
    }

    /* ====== Gas 查找与信息 ====== */

    @Nullable
    public static Gas getChemical(@Nullable ResourceLocation id) {
        if (id == null) return null;
        Gas gas = MekanismAPI.gasRegistry().getValue(id);
        if (gas == null || gas.isEmptyType()) return null;
        return gas;
    }

    @Nullable
    public static Component getChemicalNameByKind(MekChemicalKind kind, @Nullable ResourceLocation id) {
        if (id == null) return null;
        switch (kind) {
            case GAS -> {
                Gas gas = MekanismAPI.gasRegistry().getValue(id);
                return (gas != null && !gas.isEmptyType()) ? gas.getTextComponent() : null;
            }
            case INFUSION -> {
                InfuseType type = MekanismAPI.infuseTypeRegistry().getValue(id);
                return (type != null && !type.isEmptyType()) ? type.getTextComponent() : null;
            }
            case PIGMENT -> {
                Pigment pigment = MekanismAPI.pigmentRegistry().getValue(id);
                return (pigment != null && !pigment.isEmptyType()) ? pigment.getTextComponent() : null;
            }
            case SLURRY -> {
                Slurry slurry = MekanismAPI.slurryRegistry().getValue(id);
                return (slurry != null && !slurry.isEmptyType()) ? slurry.getTextComponent() : null;
            }
        }
        return null;
    }

    @Nullable
    public static Component getChemicalName(@Nullable ResourceLocation id) {
        if (id == null) return null;
        for (MekChemicalKind kind : MekChemicalKind.values()) {
            Component name = getChemicalNameByKind(kind, id);
            if (name != null) return name;
        }
        return null;
    }

    /* ====== 化学品推送（无限流体机器 BOTH 模式主动输出） ====== */

    public static long pushAnyChemical(Level level, BlockPos pos, Direction dir,
                                       MekChemicalKind kind, ResourceLocation id, long amount) {
        if (amount <= 0) return 0;
        return switch (kind) {
            case GAS      -> pushGas(level, pos, dir, id, amount);
            case INFUSION -> pushInfusion(level, pos, dir, id, amount);
            case PIGMENT  -> pushPigment(level, pos, dir, id, amount);
            case SLURRY   -> pushSlurry(level, pos, dir, id, amount);
        };
    }

    public static long pushGas(Level level, BlockPos pos, Direction dir,
                               ResourceLocation id, long amount) {
        if (amount <= 0) return 0;
        Gas gas = MekanismAPI.gasRegistry().getValue(id);
        if (gas == null) return 0;
        BlockEntity target = level.getBlockEntity(pos.relative(dir));
        if (target == null) return 0;
        long[] result = {0};
        target.getCapability(GAS_HANDLER_CAP, dir.getOpposite()).ifPresent(handler -> {
            GasStack stack = new GasStack(gas, amount);
            GasStack remainingSim = handler.insertChemical(stack, Action.SIMULATE);
            long inserted = amount - remainingSim.getAmount();
            if (inserted <= 0) return;
            handler.insertChemical(new GasStack(gas, inserted), Action.EXECUTE);
            result[0] = inserted;
        });
        return result[0];
    }

    public static long pushInfusion(Level level, BlockPos pos, Direction dir,
                                    ResourceLocation id, long amount) {
        if (amount <= 0) return 0;
        InfuseType type = MekanismAPI.infuseTypeRegistry().getValue(id);
        if (type == null) return 0;
        BlockEntity target = level.getBlockEntity(pos.relative(dir));
        if (target == null) return 0;
        long[] result = {0};
        target.getCapability(INFUSION_HANDLER_CAP, dir.getOpposite()).ifPresent(handler -> {
            InfusionStack stack = new InfusionStack(type, amount);
            InfusionStack remainingSim = handler.insertChemical(stack, Action.SIMULATE);
            long inserted = amount - remainingSim.getAmount();
            if (inserted <= 0) return;
            handler.insertChemical(new InfusionStack(type, inserted), Action.EXECUTE);
            result[0] = inserted;
        });
        return result[0];
    }

    public static long pushPigment(Level level, BlockPos pos, Direction dir,
                                   ResourceLocation id, long amount) {
        if (amount <= 0) return 0;
        Pigment pigment = MekanismAPI.pigmentRegistry().getValue(id);
        if (pigment == null) return 0;
        BlockEntity target = level.getBlockEntity(pos.relative(dir));
        if (target == null) return 0;
        long[] result = {0};
        target.getCapability(PIGMENT_HANDLER_CAP, dir.getOpposite()).ifPresent(handler -> {
            PigmentStack stack = new PigmentStack(pigment, amount);
            PigmentStack remainingSim = handler.insertChemical(stack, Action.SIMULATE);
            long inserted = amount - remainingSim.getAmount();
            if (inserted <= 0) return;
            handler.insertChemical(new PigmentStack(pigment, inserted), Action.EXECUTE);
            result[0] = inserted;
        });
        return result[0];
    }

    public static long pushSlurry(Level level, BlockPos pos, Direction dir,
                                  ResourceLocation id, long amount) {
        if (amount <= 0) return 0;
        Slurry slurry = MekanismAPI.slurryRegistry().getValue(id);
        if (slurry == null) return 0;
        BlockEntity target = level.getBlockEntity(pos.relative(dir));
        if (target == null) return 0;
        long[] result = {0};
        target.getCapability(SLURRY_HANDLER_CAP, dir.getOpposite()).ifPresent(handler -> {
            SlurryStack stack = new SlurryStack(slurry, amount);
            SlurryStack remainingSim = handler.insertChemical(stack, Action.SIMULATE);
            long inserted = amount - remainingSim.getAmount();
            if (inserted <= 0) return;
            handler.insertChemical(new SlurryStack(slurry, inserted), Action.EXECUTE);
            result[0] = inserted;
        });
        return result[0];
    }

    /* ====== 化学品抽取并销毁（销毁机器 BOTH 模式主动抽取） ====== */

    /**
     * 从相邻方块主动抽取任意化学品（Gas/Infusion/Pigment/Slurry）并将其虚空销毁。
     * <p>
     * 按顺序尝试四种化学品类型，返回实际销毁的总量。
     *
     * @param level  世界
     * @param pos    销毁机器的位置
     * @param dir    抽取方向
     * @param budget 当前 tick 可销毁的最大量（mB）
     * @return 实际销毁的化学品量（mB）
     */
    public static long drainAndDestroyAnyChemical(Level level, BlockPos pos,
                                                   Direction dir, long budget) {
        if (budget <= 0) return 0;
        long consumed = 0;

        BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
        if (neighbor == null) return 0;
        Direction side = dir.getOpposite();

        // Gas
        long gasConsumed = drainChemical(neighbor, GAS_HANDLER_CAP, side, budget - consumed);
        consumed += gasConsumed;

        // Infusion
        if (consumed < budget) {
            long c = drainChemical(neighbor, INFUSION_HANDLER_CAP, side, budget - consumed);
            consumed += c;
        }

        // Pigment
        if (consumed < budget) {
            long c = drainChemical(neighbor, PIGMENT_HANDLER_CAP, side, budget - consumed);
            consumed += c;
        }

        // Slurry
        if (consumed < budget) {
            long c = drainChemical(neighbor, SLURRY_HANDLER_CAP, side, budget - consumed);
            consumed += c;
        }

        return consumed;
    }

    @SuppressWarnings("unchecked")
    private static <T extends mekanism.api.chemical.IChemicalHandler<?, ?>>
    long drainChemical(BlockEntity be, Capability<T> cap, Direction side, long budget) {
        if (budget <= 0) return 0;
        long[] consumed = {0};
        be.getCapability(cap, side).ifPresent(handler -> {
            // 先模拟提取
            var simResult = handler.extractChemical(budget, Action.SIMULATE);
            if (simResult == null || simResult.isEmpty()) return;
            // 执行提取（丢弃 = 虚空销毁）
            var extracted = handler.extractChemical(simResult.getAmount(), Action.EXECUTE);
            if (extracted != null && !extracted.isEmpty()) {
                consumed[0] = extracted.getAmount();
            }
        });
        return consumed[0];
    }

    /* ====== tryGetAnyChemicalFromHandler（原有方法保持不变） ====== */

    @Nullable
    public static MekChemicalBinding tryGetAnyChemicalFromHandler(Level level, BlockPos pos,
                                                                  @Nullable Direction preferredSide) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        MekChemicalBinding b;
        b = tryGetGas(be, preferredSide);     if (b != null) return b;
        b = tryGetInfusion(be, preferredSide); if (b != null) return b;
        b = tryGetPigment(be, preferredSide);  if (b != null) return b;
        b = tryGetSlurry(be, preferredSide);
        return b;
    }

    @Nullable
    private static MekChemicalBinding tryGetGas(BlockEntity be, @Nullable Direction side) {
        ResourceLocation id = tryGetIdGeneric(be, GAS_HANDLER_CAP, side, MekChemicalKind.GAS);
        return id == null ? null : new MekChemicalBinding(MekChemicalKind.GAS, id);
    }

    @Nullable
    private static MekChemicalBinding tryGetInfusion(BlockEntity be, @Nullable Direction side) {
        ResourceLocation id = tryGetIdGeneric(be, INFUSION_HANDLER_CAP, side, MekChemicalKind.INFUSION);
        return id == null ? null : new MekChemicalBinding(MekChemicalKind.INFUSION, id);
    }

    @Nullable
    private static MekChemicalBinding tryGetPigment(BlockEntity be, @Nullable Direction side) {
        ResourceLocation id = tryGetIdGeneric(be, PIGMENT_HANDLER_CAP, side, MekChemicalKind.PIGMENT);
        return id == null ? null : new MekChemicalBinding(MekChemicalKind.PIGMENT, id);
    }

    @Nullable
    private static MekChemicalBinding tryGetSlurry(BlockEntity be, @Nullable Direction side) {
        ResourceLocation id = tryGetIdGeneric(be, SLURRY_HANDLER_CAP, side, MekChemicalKind.SLURRY);
        return id == null ? null : new MekChemicalBinding(MekChemicalKind.SLURRY, id);
    }

    @Nullable
    private static <T> ResourceLocation tryGetIdGeneric(BlockEntity be, Capability<T> cap,
                                                        @Nullable Direction preferredSide,
                                                        MekChemicalKind kind) {
        if (preferredSide != null) {
            ResourceLocation id = be.getCapability(cap, preferredSide).resolve()
                    .map(h -> firstNonEmptyId(kind, h)).orElse(null);
            if (id != null) return id;
        }
        for (Direction d : Direction.values()) {
            ResourceLocation id = be.getCapability(cap, d).resolve()
                    .map(h -> firstNonEmptyId(kind, h)).orElse(null);
            if (id != null) return id;
        }
        try {
            return be.getCapability(cap, null).resolve()
                    .map(h -> firstNonEmptyId(kind, h)).orElse(null);
        } catch (Throwable ignored) { return null; }
    }

    @Nullable
    private static ResourceLocation firstNonEmptyId(MekChemicalKind kind, Object handler) {
        if (handler == null) return null;
        switch (kind) {
            case GAS -> {
                IGasHandler h = (IGasHandler) handler;
                for (int i = 0; i < h.getTanks(); i++) {
                    GasStack st = h.getChemicalInTank(i);
                    if (!st.isEmpty()) return MekanismAPI.gasRegistry().getKey(st.getType());
                }
            }
            case INFUSION -> {
                IInfusionHandler h = (IInfusionHandler) handler;
                for (int i = 0; i < h.getTanks(); i++) {
                    var st = h.getChemicalInTank(i);
                    if (!st.isEmpty()) return MekanismAPI.infuseTypeRegistry().getKey(st.getType());
                }
            }
            case PIGMENT -> {
                IPigmentHandler h = (IPigmentHandler) handler;
                for (int i = 0; i < h.getTanks(); i++) {
                    var st = h.getChemicalInTank(i);
                    if (!st.isEmpty()) return MekanismAPI.pigmentRegistry().getKey(st.getType());
                }
            }
            case SLURRY -> {
                ISlurryHandler h = (ISlurryHandler) handler;
                for (int i = 0; i < h.getTanks(); i++) {
                    var st = h.getChemicalInTank(i);
                    if (!st.isEmpty()) return MekanismAPI.slurryRegistry().getKey(st.getType());
                }
            }
        }
        return null;
    }

    public record MekChemicalBinding(MekChemicalKind kind, ResourceLocation id) {}
}
