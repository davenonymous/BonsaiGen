package com.davenonymous.bonsaigen.datagen;

import com.davenonymous.bonsaigen.BonsaiGen;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modid = BonsaiGen.MODID)
public class DGHandler {
	@SuppressWarnings("ConstantConditions")
	@SubscribeEvent
	public static void gatherData(GatherDataEvent event) {
		DataGenerator generator = event.getGenerator();
		PackOutput output = generator.getPackOutput();
		ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
		CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

		// Soils
		generator.addProvider(event.includeServer(), (DataProvider.Factory<DatapackBuiltinEntriesProvider>) soilOutput -> new DatapackBuiltinEntriesProvider(
			soilOutput,
			event.getLookupProvider(),
			DGDataRegistries.create(), Set.of(BonsaiGen.MODID)
		));

	}
}
