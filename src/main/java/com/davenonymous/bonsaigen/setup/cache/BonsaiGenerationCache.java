package com.davenonymous.bonsaigen.setup.cache;

import com.davenonymous.bonsaigen.setup.ModDataMaps;
import com.davenonymous.bonsaigen.setup.data.BonsaiGenerationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;

import java.util.HashMap;
import java.util.Map;

public class BonsaiGenerationCache {
	public static final Map<Item, BonsaiGenerationInfo> BONSAI_GENERATION = new HashMap<>();

	public static void dataMapsUpdated(DataMapsUpdatedEvent event) {
		event.ifRegistry(
			Registries.ITEM, itemRegistry -> {

				BONSAI_GENERATION.clear();
				Map<ResourceKey<Item>, BonsaiGenerationInfo> seedData = itemRegistry.getDataMap(ModDataMaps.BONSAI_GENERATION);
				for(Map.Entry<ResourceKey<Item>, BonsaiGenerationInfo> entry : seedData.entrySet()) {
					BONSAI_GENERATION.put(itemRegistry.get(entry.getKey()), entry.getValue());
				}
			}
		);
	}
}
