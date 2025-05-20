package com.davenonymous.bonsaigen.setup;

import com.davenonymous.bonsaigen.BonsaiGen;
import com.davenonymous.bonsaigen.setup.data.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

@EventBusSubscriber(modid = BonsaiGen.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModDataMaps {
	public static final DataMapType<Item, BonsaiInfo> BONSAI = DataMapType
		.builder(
			BonsaiGen.bonsaiResource("bonsai"),
			Registries.ITEM,
			BonsaiInfo.CODEC
		)
		.synced(BonsaiInfo.CODEC, false)
		.build();

	public static final DataMapType<Item, ModelGenerationInfo> FIXED_TREE_GENERATION_SEEDS = DataMapType
		.builder(
			BonsaiGen.resource("generation_config"),
			Registries.ITEM,
			ModelGenerationInfo.CODEC.codec()
		)
		.synced(ModelGenerationInfo.CODEC.codec(), false)
		.build();

	public static final DataMapType<Item, BonsaiGenerationInfo> BONSAI_GENERATION = DataMapType
		.builder(
			BonsaiGen.resource("bonsai_generation"),
			Registries.ITEM,
			BonsaiGenerationInfo.CODEC
		)
		.synced(BonsaiGenerationInfo.CODEC, false)
		.build();


	@SubscribeEvent
	private static void registerDataMapTypes(RegisterDataMapTypesEvent event) {
		event.register(BONSAI);
		event.register(FIXED_TREE_GENERATION_SEEDS);
		event.register(BONSAI_GENERATION);
	}

}
