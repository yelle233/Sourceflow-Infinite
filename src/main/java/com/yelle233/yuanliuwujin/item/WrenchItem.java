package com.yelle233.yuanliuwujin.item;

import com.yelle233.yuanliuwujin.blockentity.ICoreMachine;
import com.yelle233.yuanliuwujin.blockentity.IVoidGenerator;
import com.yelle233.yuanliuwujin.client.RateInputScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 扳手物品（1.20.1 Forge v2.0 版本）。
 * <p>
 * <b>两种模式（Shift+滚轮切换）：</b>
 * <ul>
 *   <li><b>IO 模式</b>：非潜行右键从副手插入核心；潜行右键取出核心</li>
 *   <li><b>CONFIG 模式</b>：右键侧面循环切换面模式（OFF/PULL/BOTH 或 OFF/PUSH/BOTH）</li>
 *   <li>CONFIG 模式下，潜行右键侧面 → 速率 +10（单击）或持续增加（长按，客户端处理）</li>
 *   <li>CONFIG 模式下，Shift+滚轮上/下 → 速率 ±1000（通过网络包处理）</li>
 * </ul>
 * <p>
 * 面速率的实际调整由 {@link com.yelle233.yuanliuwujin.network.FaceRateUpdateMessage} 处理，
 * 客户端长按逻辑由 {@link com.yelle233.yuanliuwujin.SourceflowInfiniteClient} 处理。
 * 此处 {@code useOn} 只处理单次点击。
 * <p>
 * 模式存储使用 NBT Tag（1.20.1 Forge 不支持 DataComponents）。
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties props) { super(props); }

    // ── 模式枚举 ──────────────────────────────────────────

    public enum WrenchMode {
        IO, CONFIG;

        public WrenchMode next(int delta) {
            int i = (this.ordinal() + (delta > 0 ? 1 : -1) + values().length) % values().length;
            return values()[i];
        }
    }

    private static final String TAG_MODE = "WrenchMode";

    /** 从 NBT 读取当前模式，默认 IO */
    public static WrenchMode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MODE)) return WrenchMode.IO;
        try { return WrenchMode.valueOf(tag.getString(TAG_MODE)); }
        catch (IllegalArgumentException e) { return WrenchMode.IO; }
    }

    /** 将模式写入 NBT */
    public static void setMode(ItemStack stack, WrenchMode mode) {
        stack.getOrCreateTag().putString(TAG_MODE, mode.name());
    }

    // ── 右键交互 ──────────────────────────────────────────

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);

        // 虚空发电机：只支持 CONFIG 模式
        if (be instanceof IVoidGenerator generator) {
            Player player = ctx.getPlayer();
            if (player == null) return InteractionResult.PASS;

            WrenchMode mode = getMode(ctx.getItemInHand());
            Direction face = ctx.getClickedFace();

            if (mode == WrenchMode.CONFIG) {
                return handleGeneratorConfigMode(level, pos, player, generator, face, player.isShiftKeyDown());
            } else {
                // IO 模式：潜行右键拆除
                if (player.isShiftKeyDown() && !level.isClientSide) {
                    return dismantleGenerator(level, pos, player);
                }
                return InteractionResult.PASS;
            }
        }

        if (!(be instanceof ICoreMachine machine)) return InteractionResult.PASS;

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // 记录玩家交互（用于成就触发）
        if (!level.isClientSide) {
            machine.setLastInteractingPlayer(player);
        }

        WrenchMode mode = getMode(ctx.getItemInHand());
        Direction face  = ctx.getClickedFace();

        if (mode == WrenchMode.IO) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            return handleIOMode(level, pos, player, machine);
        } else {
            return handleConfigMode(level, pos, player, machine, face);
        }
    }

    // ── IO 模式：插入/取出核心 ────────────────────────────

    private InteractionResult handleIOMode(Level level, BlockPos pos, Player player, ICoreMachine machine) {
        ItemStack offhand = player.getOffhandItem();

        // 潜行右键：拆除机器
        if (player.isShiftKeyDown()) {
            return dismantleMachine(level, pos, player, machine);
        }

        // 左手空着：取出核心
        if (offhand.isEmpty()) {
            ItemStack core = machine.getCoreSlot().getStackInSlot(0);
            if (core.isEmpty()) return InteractionResult.PASS;
            machine.getCoreSlot().setStackInSlot(0, ItemStack.EMPTY);
            machine.onCoreChanged();
            player.addItem(core);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        // 左手有核心：插入核心
        if (!machine.isValidCoreItem(offhand.getItem())) return InteractionResult.PASS;
        if (!machine.getCoreSlot().getStackInSlot(0).isEmpty()) return InteractionResult.PASS;

        ItemStack toInsert = offhand.copy();
        toInsert.setCount(1);
        machine.getCoreSlot().setStackInSlot(0, toInsert);
        machine.onCoreChanged();
        offhand.shrink(1);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    // ── 拆除机器 ──────────────────────────────────────────

    private InteractionResult dismantleMachine(Level level, BlockPos pos,
                                                Player player, ICoreMachine machine) {
        BlockState state = level.getBlockState(pos);

        // 取出核心并返还给玩家
        ItemStack core = machine.getCoreSlot().getStackInSlot(0);
        if (!core.isEmpty()) {
            if (!player.getInventory().add(core.copy())) {
                player.drop(core.copy(), false);
            }
            // 清空核心槽位，避免 onRemove 重复掉落
            machine.getCoreSlot().setStackInSlot(0, ItemStack.EMPTY);
        }

        // 掉落机器方块
        ItemStack blockItem = new ItemStack(state.getBlock());
        if (!player.getInventory().add(blockItem)) {
            player.drop(blockItem, false);
        }

        // 移除方块
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);

        return InteractionResult.SUCCESS;
    }

    // ── 拆除虚空发电机 ──

    private InteractionResult dismantleGenerator(Level level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        ItemStack blockItem = new ItemStack(state.getBlock());
        if (!player.getInventory().add(blockItem)) player.drop(blockItem, false);
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    // ── CONFIG 模式：切换面模式 / 调整速率 ────────────────

    private InteractionResult handleConfigMode(Level level, BlockPos pos, Player player,
                                               ICoreMachine machine, Direction face) {
        if (face == Direction.UP) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            // 客户端：打开速率输入界面
            if (level.isClientSide) {
                Minecraft.getInstance().setScreen(new RateInputScreen(pos, face, machine.getFaceRate(face)));
            }
            return InteractionResult.SUCCESS;
        }

        // 服务端：切换面模式
        if (!level.isClientSide) {
            machine.cycleSideMode(face);
            level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.5f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    // ── 虚空发电机 CONFIG 模式 ──

    private InteractionResult handleGeneratorConfigMode(Level level, BlockPos pos,
                                                         Player player, IVoidGenerator generator,
                                                         Direction face, boolean sneaking) {
        // 底部不能配置
        if (face == Direction.DOWN) return InteractionResult.PASS;

        if (sneaking) {
            // 客户端：打开速率输入界面
            if (level.isClientSide) {
                Minecraft.getInstance().setScreen(new RateInputScreen(pos, face, generator.getSideRate(face)));
            }
            return InteractionResult.SUCCESS;
        }

        // 服务端：切换侧面输出状态
        if (!level.isClientSide) {
            generator.toggleSideOutput(face);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }
}

