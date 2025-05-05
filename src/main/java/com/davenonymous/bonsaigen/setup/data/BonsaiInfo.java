package com.davenonymous.bonsaigen.setup.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public record BonsaiInfo(ResourceLocation model, Optional<List<ResourceLocation>> validSoils, Optional<Integer> requiredTicks, Optional<Integer> lightEmission) {
	public static final Codec<BonsaiInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ResourceLocation.CODEC.fieldOf("model").forGetter(BonsaiInfo::model),
		ResourceLocation.CODEC.listOf().optionalFieldOf("valid_soil_types").forGetter(BonsaiInfo::validSoils),
		Codec.INT.optionalFieldOf("base_ticks").forGetter(BonsaiInfo::requiredTicks),
		Codec.INT.optionalFieldOf("light_emission").forGetter(BonsaiInfo::lightEmission)
	).apply(instance, BonsaiInfo::new));

	public static BonsaiInfo plain(ResourceLocation model) {
		return new BonsaiInfo(model, Optional.empty(), Optional.empty(), Optional.empty());
	}

	public static BonsaiInfo of(ResourceLocation... validSoils) {
		return new BonsaiInfo(null, Optional.of(Arrays.stream(validSoils).toList()), Optional.empty(), Optional.empty());
	}

	public BonsaiInfo with(BonsaiGenerationInfo genInfo) {
		var newValidSoils = genInfo.validSoils().isEmpty() ? validSoils : genInfo.validSoils();
		var newRequiredTicks = genInfo.requiredTicks().isEmpty() ? requiredTicks : genInfo.requiredTicks();
		var newLightEmission = genInfo.lightEmission().isEmpty() ? lightEmission : genInfo.lightEmission();
		return new BonsaiInfo(model, newValidSoils, newRequiredTicks, newLightEmission);
	}



	@Override
	public String toString() {
		return String.format("BonsaiInfo{model=%s, baseTicks=%s, soilTypes=%s}", model, requiredTicks, validSoils);
	}

	public BonsaiInfo withModel(ResourceLocation model) {
		return new BonsaiInfo(model, validSoils, requiredTicks, lightEmission);
	}

	public BonsaiInfo withValidSoils(List<ResourceLocation> validSoils) {
		if(validSoils.isEmpty()) {
			return new BonsaiInfo(model, Optional.empty(), requiredTicks, lightEmission);
		}
		return new BonsaiInfo(model, Optional.of(validSoils), requiredTicks, lightEmission);
	}

	public BonsaiInfo withLightEmission(int lightEmission) {
		return new BonsaiInfo(model, validSoils, requiredTicks, lightEmission == 0 ? Optional.empty() : Optional.of(lightEmission));
	}
}
