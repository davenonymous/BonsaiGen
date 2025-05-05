package com.davenonymous.bonsaigen.setup.cache;

import com.davenonymous.bonsaigen.setup.ModDataMaps;
import com.davenonymous.bonsaigen.setup.data.ModelGenerationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;

import java.util.HashMap;
import java.util.Map;

public class FixedTreeGenerationCache {
	public static final Map<Item, ModelGenerationInfo> FIXED_TREE_GENERATION = new HashMap<>();

	public static void dataMapsUpdated(DataMapsUpdatedEvent event) {
		event.ifRegistry(Registries.ITEM, itemRegistry -> {

			FIXED_TREE_GENERATION.clear();
			Map<ResourceKey<Item>, ModelGenerationInfo> seedData = itemRegistry.getDataMap(ModDataMaps.FIXED_TREE_GENERATION_SEEDS);
			for(Map.Entry<ResourceKey<Item>, ModelGenerationInfo> entry : seedData.entrySet()) {
				FIXED_TREE_GENERATION.put(itemRegistry.get(entry.getKey()), entry.getValue());
			}
		});
	}
}
