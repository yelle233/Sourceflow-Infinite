package com.yelle233.yuanliuwujin.advancement;

import com.google.gson.JsonObject;
import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.advancements.critereon.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 机器爆炸触发器（1.20.1 Forge版本）
 * <p>
 * 当玩家导致机器爆炸时触发此成就
 */
public class MachineExplosionTrigger extends SimpleCriterionTrigger<MachineExplosionTrigger.TriggerInstance> {

    private static final ResourceLocation ID = new ResourceLocation(SourceflowInfinite.MODID, "machine_explosion");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        return new TriggerInstance(player);
    }

    /**
     * 触发机器爆炸成就
     */
    public void trigger(ServerPlayer player) {
        this.trigger(player, triggerInstance -> true);
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {
        public TriggerInstance(ContextAwarePredicate player) {
            super(ID, player);
        }

        public static TriggerInstance machineExploded() {
            return new TriggerInstance(ContextAwarePredicate.ANY);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            return super.serializeToJson(context);
        }
    }
}
