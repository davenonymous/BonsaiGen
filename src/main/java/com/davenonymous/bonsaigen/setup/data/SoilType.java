package com.davenonymous.bonsaigen.setup.data;

import com.davenonymous.bonsaigen.BonsaiGen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SoilType(ResourceLocation id, ItemStack defaultItem, String translationKey) {

	public SoilType(String name, ItemStack defaultItem, String translationKey) {
		this(BonsaiGen.resource(name), defaultItem, translationKey);
	}

	private static final MapCodec<SoilType> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
		return instance.group(
			ResourceLocation.CODEC.fieldOf("id").forGetter(SoilType::id),
			ItemStack.CODEC.fieldOf("defaultItem").forGetter(SoilType::defaultItem),
			Codec.STRING.fieldOf("translationKey").forGetter(SoilType::translationKey)
		).apply(instance, SoilType::new);
	});

	public static Codec<SoilType> codec() {
		return CODEC.codec();
	}
}
