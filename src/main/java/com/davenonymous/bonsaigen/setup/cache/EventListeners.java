package com.davenonymous.bonsaigen.setup.cache;


import com.davenonymous.bonsaigen.BonsaiGen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;

@EventBusSubscriber(modid = BonsaiGen.MODID, bus = EventBusSubscriber.Bus.GAME)
public class EventListeners {
	@SubscribeEvent
	public static void dataMapsUpdated(DataMapsUpdatedEvent event) {
		FixedTreeGenerationCache.dataMapsUpdated(event);
		BonsaiGenerationCache.dataMapsUpdated(event);
		SoilTypeGenerationCache.dataMapsUpdated(event);
	}
}
