package com.davenonymous.bonsaitrees.setup;

import com.davenonymous.bonsaitrees.BonsaiTrees;
import com.davenonymous.bonsaitrees.setup.data.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

@EventBusSubscriber(modid = BonsaiTrees.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModDataMaps {
	public static final DataMapType<Item, BonsaiInfo> BONSAI = DataMapType
		.builder(
			BonsaiTrees.resource("bonsai"),
			Registries.ITEM,
			BonsaiInfo.CODEC
		)
		.synced(BonsaiInfo.CODEC, false)
		.build();

	public static final DataMapType<Block, SoilInfo> BLOCK_SOIL = DataMapType
		.builder(
			BonsaiTrees.resource("soil"),
			Registries.BLOCK,
			SoilInfo.CODEC
		)
		.synced(SoilInfo.CODEC, false)
		.build();

	public static final DataMapType<Fluid, SoilInfo> FLUID_SOIL = DataMapType
		.builder(
			BonsaiTrees.resource("soil"),
			Registries.FLUID,
			SoilInfo.CODEC
		)
		.synced(SoilInfo.CODEC, false)
		.build();

	public static final DataMapType<Item, SoilInfoWithTexture> ITEM_SOIL = DataMapType
		.builder(
			BonsaiTrees.resource("soil"),
			Registries.ITEM,
			SoilInfoWithTexture.CODEC
		)
		.synced(SoilInfoWithTexture.CODEC, false)
		.build();

	public static final DataMapType<Item, ModelGenerationInfo> FIXED_TREE_GENERATION_SEEDS = DataMapType
		.builder(
			BonsaiTrees.resource("fixed_tree_generation"),
			Registries.ITEM,
			ModelGenerationInfo.CODEC.codec()
		)
		.synced(ModelGenerationInfo.CODEC.codec(), false)
		.build();

	public static final DataMapType<Item, BonsaiGenerationInfo> BONSAI_GENERATION = DataMapType
		.builder(
			BonsaiTrees.resource("bonsai_generation"),
			Registries.ITEM,
			BonsaiGenerationInfo.CODEC
		)
		.synced(BonsaiGenerationInfo.CODEC, false)
		.build();


	@SubscribeEvent
	private static void registerDataMapTypes(RegisterDataMapTypesEvent event) {
		event.register(BONSAI);
		event.register(BLOCK_SOIL);
		event.register(FLUID_SOIL);
		event.register(ITEM_SOIL);
		event.register(FIXED_TREE_GENERATION_SEEDS);
		event.register(BONSAI_GENERATION);
	}

}
