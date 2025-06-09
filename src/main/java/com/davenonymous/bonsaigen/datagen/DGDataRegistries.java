package com.davenonymous.bonsaigen.datagen;


import com.davenonymous.bonsaigen.BonsaiGen;
import com.davenonymous.bonsaigen.setup.cache.SoilTypeGenerationCache;
import com.davenonymous.bonsaigen.setup.data.SoilTypeGenerationInfo;
import com.davenonymous.bonsaitrees.setup.data.SoilType;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import static com.davenonymous.bonsaitrees.setup.ModRegistries.SOILTYPE_REGISTRY_KEY;


public class DGDataRegistries {

	public static RegistrySetBuilder create(String modId) {
		return new RegistrySetBuilder().add(
			SOILTYPE_REGISTRY_KEY, bootstrap -> {
				var cache = SoilTypeGenerationCache.SOIL_TYPE_GENERATION_CACHE.get(modId);
				if (cache != null) {
					for (var entry : cache.entrySet()) {
						Item item = entry.getKey();
						SoilTypeGenerationInfo info = entry.getValue();

						ResourceKey<SoilType> key = ResourceKey.create(SOILTYPE_REGISTRY_KEY, info.id());
						BonsaiGen.LOGGER.info("Soil {} metadata:", info.id());
						BonsaiGen.LOGGER.info("  Default Item: {}", item);
						if(info.tags().isPresent()) {
							BonsaiGen.LOGGER.info("  Tags: {}", info.tags().get());
						}
						if(info.blocks().isPresent()) {
							BonsaiGen.LOGGER.info("  Blocks: {}", info.blocks().get());
						}
						if(info.fluids().isPresent()) {
							BonsaiGen.LOGGER.info("  Fluids: {}", info.fluids().get());
						}
						if(info.fluidTags().isPresent()) {
							BonsaiGen.LOGGER.info("  Fluid Tags: {}", info.fluidTags().get());
						}

						ItemStack defaultItem = new ItemStack(item);
						String translationKey = BonsaiGen.BASE_MODID + ".tooltip.soil." + key.location().getNamespace() + "." + key.location().getPath();
						if(info.translationKey().isPresent()) {
							translationKey = info.translationKey().get();
						}

						SoilType soilType = new SoilType(key.location(), defaultItem, translationKey);
						bootstrap.register(key, soilType);
					}
				}
			}
		);
	}
}
