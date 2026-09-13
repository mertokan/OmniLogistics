package com.mertokan.omnilogistics.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Recipe condition on one of the module flags in the config. A pack that turns mining off gets a mod with no Void Miner
 * recipes at all, instead of an uncraftable block sitting in JEI. Datapack shape:
 * {@code "neoforge:conditions": [{"type": "omnilogistics:module", "module": "mining"}]}.
 */
public record ModuleCondition(String module) implements ICondition {
    public static final MapCodec<ModuleCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.STRING.fieldOf("module").forGetter(ModuleCondition::module)).apply(i, ModuleCondition::new));

    @Override
    public boolean test(IContext context) {
        return OmniConfig.moduleEnabled(module);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
