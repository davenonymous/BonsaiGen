package com.davenonymous.bonsaigen.datagen;

import com.davenonymous.bonsaigen.client.multiblock.MultiBlockFromFeatureGenerator;
import com.davenonymous.bonsaigen.setup.cache.BonsaiGenerationCache;
import com.davenonymous.bonsaigen.setup.cache.SoilTypeGenerationCache;
import com.davenonymous.bonsaigen.setup.data.BonsaiGenerationInfo;
import com.davenonymous.bonsaigen.setup.data.SoilTypeGenerationInfo;
import com.davenonymous.bonsaitrees.multiblock.MultiBlockGeometryBase;
import com.davenonymous.bonsaitrees.setup.ModDataMaps;
import com.davenonymous.bonsaitrees.setup.data.BonsaiInfo;
import com.davenonymous.bonsaitrees.setup.data.DefaultSoilTypes;
import com.davenonymous.bonsaitrees.setup.data.SoilInfo;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.HugeFungusFeature;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.data.DataMapProvider;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DGDataMaps extends DataMapProvider {
	public final String modId;
	public final DGTreeModelProvider treeModelProvider;

	public DGDataMaps(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider, String modId, DGTreeModelProvider treeModelProvider) {
		super(packOutput, lookupProvider);
		this.modId = modId;
		this.treeModelProvider = treeModelProvider;
	}

	private final Map<ResourceLocation, BonsaiInfo> bonsaiInfoMap = new HashMap<>();

	public BonsaiGenerationInfo getBonsaiGenerationInfo(ItemLike stack) {
		return BonsaiGenerationCache.BONSAI_GENERATION.containsKey(stack.asItem())
			   ? BonsaiGenerationCache.BONSAI_GENERATION.get(stack.asItem())
			   : BonsaiGenerationInfo.EMPTY();
	}

	public Map<Item, SoilTypeGenerationInfo> getSoilTypeGenerationInfo() {
		if (!SoilTypeGenerationCache.SOIL_TYPE_GENERATION_CACHE.containsKey(modId)) {
			return Collections.emptyMap();
		}

		return SoilTypeGenerationCache.SOIL_TYPE_GENERATION_CACHE.get(modId);
	}

	public DGDataMaps addBonsai(ItemLike item, BonsaiInfo info) {
		ResourceLocation itemID = item.asItem().builtInRegistryHolder().getKey().location();
		bonsaiInfoMap.put(itemID, info);
		return this;
	}

	@Override
	protected void gather(HolderLookup.Provider foo) {
		lookupProvider.thenAccept(provider -> {
			var builder = builder(ModDataMaps.BONSAI);
			builder.conditions(new ModLoadedCondition(modId));
			treeModelProvider.itemToModel.forEach((item, model) -> {
				MultiBlockFromFeatureGenerator.Result floodfillResult = treeModelProvider.models.get(model);
				Feature<?> feature = floodfillResult.feature().feature();
				List<ResourceLocation> validSoils = new ArrayList<>();
				if(feature instanceof AbstractHugeMushroomFeature) {
					validSoils.add(DefaultSoilTypes.MYCELIUM.location());
				} else if(feature instanceof HugeFungusFeature) {
					validSoils.add(DefaultSoilTypes.NYLIUM.location());
				}

				MultiBlockGeometryBase geometry = floodfillResult.geometry();
				int lightEmission = geometry.lightEmission();
				Item bonsaiItem = provider.lookupOrThrow(Registries.ITEM).get(ResourceKey.create(Registries.ITEM, item)).get().value();
				BonsaiGenerationInfo generationInfo = getBonsaiGenerationInfo(bonsaiItem);
				if(item.getNamespace().equals(modId)) {
					if(bonsaiInfoMap.containsKey(item)) {
						BonsaiInfo info = with(bonsaiInfoMap.get(item).withModel(model).withLightEmission(lightEmission), generationInfo);
						if(info.validSoils().isEmpty()) {
							info = info.withValidSoils(validSoils);
						}
						builder.add(item, info, false);
					} else {
						builder.add(item, with(BonsaiInfo.plain(model).withLightEmission(lightEmission).withValidSoils(validSoils), generationInfo), false);
					}
				}
			});

			var soilTypeInfoMap = getSoilTypeGenerationInfo();
			if (!soilTypeInfoMap.isEmpty()) {
				var fluidBuilder = builder(ModDataMaps.FLUID_SOIL);
				fluidBuilder.conditions(new ModLoadedCondition(modId));

				var soilBuilder = builder(ModDataMaps.BLOCK_SOIL);
				soilBuilder.conditions(new ModLoadedCondition(modId));

				for(Item item : soilTypeInfoMap.keySet()) {
					SoilTypeGenerationInfo soilTypeInfo = soilTypeInfoMap.get(item);
					ResourceLocation id = soilTypeInfo.id();
					SoilInfo soilInfo = SoilInfo.of(id);

					if(soilTypeInfo.isFluid()) {
						if(soilTypeInfo.fluidTags().isPresent()) {
							for(TagKey<Fluid> tag : soilTypeInfo.fluidTags().get()) {
								fluidBuilder.add(tag, soilInfo, false);
							}
						}

						if(soilTypeInfo.fluids().isPresent()) {
							for(ResourceLocation fluid : soilTypeInfo.fluids().get()) {
								fluidBuilder.add(fluid, soilInfo, false);
							}
						}
					} else {
						if(soilTypeInfo.tags().isPresent()) {
							for(TagKey<Block> tag : soilTypeInfo.tags().get()) {
								soilBuilder.add(tag, soilInfo, false);
							}
						}

						if(soilTypeInfo.blocks().isPresent()) {
							for(ResourceLocation block : soilTypeInfo.blocks().get()) {
								soilBuilder.add(block, soilInfo, false);
							}
						}

					}

				}
			}
			/*
	   		if(this.modId.equals("minecraft")) {
			   var soilBuilder = builder(ModDataMaps.BLOCK_SOIL);
			   soilBuilder.add(BlockTags.DIRT, SoilInfo.of(_DIRT), false);
			   soilBuilder.add(ModTags.DIRTS, SoilInfo.of(_DIRT), false);
			   soilBuilder.add(BlockTags.BASE_STONE_NETHER, SoilInfo.of(_NETHER_STONE), false);
			   soilBuilder.add(BlockTags.BASE_STONE_OVERWORLD, SoilInfo.of(_STONE), false);
			   soilBuilder.add(BlockTags.MUSHROOM_GROW_BLOCK, SoilInfo.of(_MYCELIUM), false);
			   soilBuilder.add(BlockTags.NYLIUM, SoilInfo.of(_NYLIUM), false);
			   soilBuilder.add(BlockTags.SAND, SoilInfo.of(_SAND), false);
			   soilBuilder.add(Tags.Blocks.END_STONES, SoilInfo.of(_END_STONE), false);

				   var itemSoilBuilder = builder(ModDataMaps.ITEM_SOIL);
			   itemSoilBuilder.add(
						   Items.ENDER_EYE.builtInRegistryHolder(),
						   SoilInfoWithTexture.of(
									   _END_STONE, 1,
									   ResourceLocation.withDefaultNamespace("block/end_portal_frame_top")
								   ), false
					   );

				   var fluidSoilBuilder = builder(ModDataMaps.FLUID_SOIL);
			   fluidSoilBuilder.add(FluidTags.WATER, SoilInfo.of(_WATER), false);
			   fluidSoilBuilder.add(FluidTags.LAVA, SoilInfo.of(_LAVA), false);
			   }
			   */
		});
	}

	public static BonsaiInfo with(BonsaiInfo target, BonsaiGenerationInfo genInfo) {
		var newValidSoils = genInfo.validSoils().isEmpty() ? target.validSoils() : genInfo.validSoils();
		var newRequiredTicks = genInfo.requiredTicks().isEmpty() ? target.requiredTicks() : genInfo.requiredTicks();
		var newLightEmission = genInfo.lightEmission().isEmpty() ? target.lightEmission() : genInfo.lightEmission();
		return new BonsaiInfo(target.model(), newValidSoils, newRequiredTicks, newLightEmission);
	}
}
