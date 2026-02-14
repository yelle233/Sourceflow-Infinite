package com.yelle233.yuanliuwujin.compat.mekanism;

import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
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
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.MekChemicalKind;

import javax.annotation.Nullable;

/**
 * Mekanism 化学品（Gas）集成帮助类（1.20.1 Forge 版本）。
 * <p>
 * <b>重大变化</b>：Mekanism 10.4.x（1.20.1）使用分离的化学品类型系统
 * （Gas / InfuseType / Pigment / Slurry），而非后续版本的统一 Chemical。
 * 此类仅处理 Gas 类型，如需支持其他化学品类型可类似扩展。
 * <p>
 * <b>重要</b>：此类引用了 Mekanism API 类，只能在确认 Mekanism 已加载后调用！
 */
public final class MekChemicalHelper {

    private MekChemicalHelper() {}

    /**
     * Mekanism Gas Handler Capability 引用。
     * <p>
     * 通过 Forge 的 CapabilityManager 获取 Mekanism 注册的 IGasHandler 能力。
     */
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

        // 1) 优先使用指定面
        if (preferredSide != null) {
            ResourceLocation id = be.getCapability(GAS_HANDLER_CAP, preferredSide)
                    .map(MekChemicalHelper::firstNonEmptyGasId)
                    .orElse(null);
            if (id != null) return id;
        }

        // 2) 遍历所有面
        for (Direction d : Direction.values()) {
            ResourceLocation id = be.getCapability(GAS_HANDLER_CAP, d)
                    .map(MekChemicalHelper::firstNonEmptyGasId)
                    .orElse(null);
            if (id != null) return id;
        }

        // 3) 尝试 null context
        try {
            return be.getCapability(GAS_HANDLER_CAP, null)
                    .map(MekChemicalHelper::firstNonEmptyGasId)
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
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

    /**
     * 根据 kind 从对应注册表获取化学品显示名称。
     */
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

    /* ====== Gas 推送（BOTH 模式主动输出） ====== */

    public static void pushAnyChemical(Level level, BlockPos pos, Direction dir,
                                       MekChemicalKind kind, ResourceLocation id, long amount) {
        if (amount <= 0) return;
        switch (kind) {
            case GAS -> pushGas(level, pos, dir, id, amount);
            case INFUSION -> pushInfusion(level, pos, dir, id, amount);
            case PIGMENT -> pushPigment(level, pos, dir, id, amount);
            case SLURRY -> pushSlurry(level, pos, dir, id, amount);
        }
    }

    public static void pushGas(Level level, BlockPos pos, Direction dir,
                               ResourceLocation id, long amount) {
        if (amount <= 0) return;

        Gas gas = MekanismAPI.gasRegistry().getValue(id);
        if (gas == null) return;

        BlockPos targetPos = pos.relative(dir);
        BlockEntity target = level.getBlockEntity(targetPos);
        if (target == null) return;

        Direction side = dir.getOpposite();
        target.getCapability(GAS_HANDLER_CAP, side).ifPresent(handler -> {
            GasStack stack = new GasStack(gas, amount);

            // 先模拟插入，看看能插多少
            GasStack remainingSim = handler.insertChemical(stack, Action.SIMULATE);
            long inserted = amount - remainingSim.getAmount();
            if (inserted <= 0) return;

            // 再执行插入
            handler.insertChemical(new GasStack(gas, inserted), Action.EXECUTE);
        });
    }

    public static void pushInfusion(Level level, BlockPos pos, Direction dir,
                                    ResourceLocation id, long amount) {
        if (amount <= 0) return;

        InfuseType type = MekanismAPI.infuseTypeRegistry().getValue(id);
        if (type == null) return;

        BlockPos targetPos = pos.relative(dir);
        BlockEntity target = level.getBlockEntity(targetPos);
        if (target == null) return;

        Direction side = dir.getOpposite();
        target.getCapability(INFUSION_HANDLER_CAP, side).ifPresent(handler -> {
            InfusionStack stack = new InfusionStack(type, amount);

            InfusionStack remainingSim = handler.insertChemical(stack, Action.SIMULATE);
            long inserted = amount - remainingSim.getAmount();
            if (inserted <= 0) return;

            handler.insertChemical(new InfusionStack(type, inserted), Action.EXECUTE);
        });
    }

    public static void pushPigment(Level level, BlockPos pos, Direction dir,
                                   ResourceLocation id, long amount) {
        if (amount <= 0) return;

        Pigment pigment = MekanismAPI.pigmentRegistry().getValue(id);
        if (pigment == null) return;

        BlockPos targetPos = pos.relative(dir);
        BlockEntity target = level.getBlockEntity(targetPos);
        if (target == null) return;

        Direction side = dir.getOpposite();
        target.getCapability(PIGMENT_HANDLER_CAP, side).ifPresent(handler -> {
            PigmentStack stack = new PigmentStack(pigment, amount);

            PigmentStack remainingSim = handler.insertChemical(stack, Action.SIMULATE);
            long inserted = amount - remainingSim.getAmount();
            if (inserted <= 0) return;

            handler.insertChemical(new PigmentStack(pigment, inserted), Action.EXECUTE);
        });
    }

    public static void pushSlurry(Level level, BlockPos pos, Direction dir,
                                  ResourceLocation id, long amount) {
        if (amount <= 0) return;

        Slurry slurry = MekanismAPI.slurryRegistry().getValue(id);
        if (slurry == null) return;

        BlockPos targetPos = pos.relative(dir);
        BlockEntity target = level.getBlockEntity(targetPos);
        if (target == null) return;

        Direction side = dir.getOpposite();
        target.getCapability(SLURRY_HANDLER_CAP, side).ifPresent(handler -> {
            SlurryStack stack = new SlurryStack(slurry, amount);

            SlurryStack remainingSim = handler.insertChemical(stack, Action.SIMULATE);
            long inserted = amount - remainingSim.getAmount();
            if (inserted <= 0) return;

            handler.insertChemical(new SlurryStack(slurry, inserted), Action.EXECUTE);
        });
    }




    @Nullable
    public static MekChemicalBinding tryGetAnyChemicalFromHandler(Level level, BlockPos pos,
                                                                  @Nullable Direction preferredSide) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        // 按顺序尝试：Gas -> Infusion -> Pigment -> Slurry
        MekChemicalBinding b;

        b = tryGetGas(level, be, preferredSide);
        if (b != null) return b;

        b = tryGetInfusion(level, be, preferredSide);
        if (b != null) return b;

        b = tryGetPigment(level, be, preferredSide);
        if (b != null) return b;

        b = tryGetSlurry(level, be, preferredSide);
        return b;
    }

    @Nullable
    private static MekChemicalBinding tryGetGas(Level level, BlockEntity be, @Nullable Direction side) {
        ResourceLocation id = tryGetIdGeneric(be, GAS_HANDLER_CAP, side, MekChemicalKind.GAS);
        return id == null ? null : new MekChemicalBinding(MekChemicalKind.GAS, id);
    }

    @Nullable
    private static MekChemicalBinding tryGetInfusion(Level level, BlockEntity be, @Nullable Direction side) {
        ResourceLocation id = tryGetIdGeneric(be, INFUSION_HANDLER_CAP, side, MekChemicalKind.INFUSION);
        return id == null ? null : new MekChemicalBinding(MekChemicalKind.INFUSION, id);
    }

    @Nullable
    private static MekChemicalBinding tryGetPigment(Level level, BlockEntity be, @Nullable Direction side) {
        ResourceLocation id = tryGetIdGeneric(be, PIGMENT_HANDLER_CAP, side, MekChemicalKind.PIGMENT);
        return id == null ? null : new MekChemicalBinding(MekChemicalKind.PIGMENT, id);
    }

    @Nullable
    private static MekChemicalBinding tryGetSlurry(Level level, BlockEntity be, @Nullable Direction side) {
        ResourceLocation id = tryGetIdGeneric(be, SLURRY_HANDLER_CAP, side, MekChemicalKind.SLURRY);
        return id == null ? null : new MekChemicalBinding(MekChemicalKind.SLURRY, id);
    }

    @Nullable
    private static <T> ResourceLocation tryGetIdGeneric(BlockEntity be, Capability<T> cap,
                                                        @Nullable Direction preferredSide,
                                                        MekChemicalKind kind) {

        // 1) 指定面
        if (preferredSide != null) {

            ResourceLocation id = be.getCapability(cap, preferredSide)
                    .resolve()
                    .map(h -> firstNonEmptyId(kind, h))
                    .orElse(null);
            if (id != null) return id;
        }
        // 2) 遍历面
        for (Direction d : Direction.values()) {
            ResourceLocation id = be.getCapability(cap, d)
                    .resolve()
                    .map(h -> firstNonEmptyId(kind, h))
                    .orElse(null);
            if (id != null) return id;
        }
        // 3) null context
        try {
            return be.getCapability(cap, null)
                    .resolve()
                    .map(h -> firstNonEmptyId(kind, h))
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static ResourceLocation firstNonEmptyId(MekChemicalKind kind, Object handler) {
        if (handler == null) return null;

        // 这里按不同 handler 调不同 stack / registry
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
                    var st = h.getChemicalInTank(i); // InfusionStack
                    if (!st.isEmpty()) return MekanismAPI.infuseTypeRegistry().getKey(st.getType());
                }
            }
            case PIGMENT -> {
                IPigmentHandler h = (IPigmentHandler) handler;
                for (int i = 0; i < h.getTanks(); i++) {
                    var st = h.getChemicalInTank(i); // PigmentStack
                    if (!st.isEmpty()) return MekanismAPI.pigmentRegistry().getKey(st.getType());
                }
            }
            case SLURRY -> {
                ISlurryHandler h = (ISlurryHandler) handler;
                for (int i = 0; i < h.getTanks(); i++) {
                    var st = h.getChemicalInTank(i); // SlurryStack
                    if (!st.isEmpty()) return MekanismAPI.slurryRegistry().getKey(st.getType());
                }
            }
        }
        return null;
    }



    //返回MEK的“kind + id”
    public record MekChemicalBinding(MekChemicalKind kind, ResourceLocation id) {}
}
