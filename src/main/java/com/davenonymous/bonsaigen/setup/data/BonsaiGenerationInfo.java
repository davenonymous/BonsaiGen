package com.davenonymous.bonsaigen.setup.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record BonsaiGenerationInfo(Optional<List<ResourceLocation>> validSoils, Optional<Integer> requiredTicks, Optional<Integer> lightEmission) {
	public static final Codec<BonsaiGenerationInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ResourceLocation.CODEC.listOf().optionalFieldOf("valid_soil_types").forGetter(BonsaiGenerationInfo::validSoils),
		Codec.INT.optionalFieldOf("base_ticks").forGetter(BonsaiGenerationInfo::requiredTicks),
		Codec.INT.optionalFieldOf("light_emission").forGetter(BonsaiGenerationInfo::lightEmission)
	).apply(instance, BonsaiGenerationInfo::new));

	public static BonsaiGenerationInfo EMPTY() {
		return new BonsaiGenerationInfo(Optional.empty(), Optional.empty(), Optional.empty());
	}
}
