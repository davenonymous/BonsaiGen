package com.davenonymous.bonsaigen.setup.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Optional;

public record SoilTypeGenerationInfo(
	ResourceLocation id, boolean isFluid,
	Optional<List<ResourceLocation>> blocks, Optional<List<TagKey<Block>>> tags,
	Optional<List<ResourceLocation>> fluids, Optional<List<TagKey<Fluid>>> fluidTags
) {
	public static final Codec<SoilTypeGenerationInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ResourceLocation.CODEC.fieldOf("id").forGetter(SoilTypeGenerationInfo::id),
		Codec.BOOL.optionalFieldOf("isFluid", false).forGetter(SoilTypeGenerationInfo::isFluid),
		ResourceLocation.CODEC.listOf().optionalFieldOf("blocks").forGetter(SoilTypeGenerationInfo::blocks),
		TagKey.codec(BuiltInRegistries.BLOCK.key()).listOf().optionalFieldOf("tags").forGetter(SoilTypeGenerationInfo::tags),
		ResourceLocation.CODEC.listOf().optionalFieldOf("fluids").forGetter(SoilTypeGenerationInfo::fluids),
		TagKey.codec(BuiltInRegistries.FLUID.key()).listOf().optionalFieldOf("fluidTags").forGetter(SoilTypeGenerationInfo::fluidTags)

	).apply(instance, SoilTypeGenerationInfo::new));
}
