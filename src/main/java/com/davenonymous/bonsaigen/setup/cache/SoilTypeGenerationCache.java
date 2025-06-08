package com.davenonymous.bonsaigen.setup.cache;

import com.davenonymous.bonsaigen.setup.ModDataMaps;
import com.davenonymous.bonsaigen.setup.data.SoilTypeGenerationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;

import java.util.HashMap;
import java.util.Map;

public class SoilTypeGenerationCache {
	public static final Map<String, Map<Item, SoilTypeGenerationInfo>> SOIL_TYPE_GENERATION_CACHE = new HashMap<>();

	public static void dataMapsUpdated(DataMapsUpdatedEvent event) {
		event.ifRegistry(Registries.ITEM, itemRegistry -> {

			SOIL_TYPE_GENERATION_CACHE.clear();
			Map<ResourceKey<Item>, SoilTypeGenerationInfo> seedData = itemRegistry.getDataMap(ModDataMaps.SOIL_TYPES_GENERATION);
			for(Map.Entry<ResourceKey<Item>, SoilTypeGenerationInfo> entry : seedData.entrySet()) {
				String modId = entry.getKey().location().getNamespace();
				SOIL_TYPE_GENERATION_CACHE.computeIfAbsent(modId, k -> new HashMap<>()).put(itemRegistry.get(entry.getKey()), entry.getValue());
			}
		});
	}
}
