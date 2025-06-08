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
						BonsaiGen.LOGGER.info("Soil metadata for item {}: {}, {}", key, info.id(), info.blocks());

						ItemStack defaultItem = new ItemStack(item);
						String translationKey = BonsaiGen.BASE_MODID + ".tooltip.soil." + key.location().getNamespace() + "." + key.location().getPath();

						SoilType soilType = new SoilType(key.location(), defaultItem, translationKey);
						// WithConditions<SoilType> soilTypeWithConditions =
						//	WithConditions.builder(soilType).addCondition(new ModLoadedCondition(key.location().getNamespace())).build();

						bootstrap.register(key, soilType);
					}
				}
			}
		);
	}
}
