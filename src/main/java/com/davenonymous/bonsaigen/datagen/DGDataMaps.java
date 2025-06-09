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

			Map<Item, SoilTypeGenerationInfo> soilTypeInfoMap = getSoilTypeGenerationInfo();
			if (!soilTypeInfoMap.isEmpty()) {
				var fluidBuilder = builder(ModDataMaps.FLUID_SOIL);
				fluidBuilder.conditions(new ModLoadedCondition(modId));

				var soilBuilder = builder(ModDataMaps.BLOCK_SOIL);
				soilBuilder.conditions(new ModLoadedCondition(modId));

				Map<TagKey<Fluid>, List<ResourceLocation>> fluidTagSoils = new HashMap<>();
				Map<TagKey<Block>, List<ResourceLocation>> blockTagSoils = new HashMap<>();
				Map<ResourceLocation, List<ResourceLocation>> fluidSoils = new HashMap<>();
				Map<ResourceLocation, List<ResourceLocation>> blockSoils = new HashMap<>();

				for(Item item : soilTypeInfoMap.keySet()) {
					SoilTypeGenerationInfo soilTypeInfo = soilTypeInfoMap.get(item);
					ResourceLocation id = soilTypeInfo.id();

					if(soilTypeInfo.isFluid()) {
						if(soilTypeInfo.fluidTags().isPresent()) {
							for(TagKey<Fluid> tag : soilTypeInfo.fluidTags().get()) {
								fluidTagSoils.computeIfAbsent(tag, k -> new ArrayList<>()).add(id);
							}
						}

						if(soilTypeInfo.fluids().isPresent()) {
							for(ResourceLocation fluid : soilTypeInfo.fluids().get()) {
								fluidSoils.computeIfAbsent(fluid, k -> new ArrayList<>()).add(id);
							}
						}
					} else {
						if(soilTypeInfo.tags().isPresent()) {
							for(TagKey<Block> tag : soilTypeInfo.tags().get()) {
								blockTagSoils.computeIfAbsent(tag, k -> new ArrayList<>()).add(id);
							}
						}

						if(soilTypeInfo.blocks().isPresent()) {
							for(ResourceLocation block : soilTypeInfo.blocks().get()) {
								blockSoils.computeIfAbsent(block, k -> new ArrayList<>()).add(id);
							}
						}
					}

					if(soilTypeInfo.isFluid()) {
						for(TagKey<Fluid> tag : fluidTagSoils.keySet()) {
							List<ResourceLocation> fluidIds = fluidTagSoils.get(tag).stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
							fluidBuilder.add(tag, SoilInfo.of(fluidIds), false);
						}
						for(ResourceLocation fluid : fluidSoils.keySet()) {
							List<ResourceLocation> fluidIds = fluidSoils.get(fluid).stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
							fluidBuilder.add(fluid, SoilInfo.of(fluidIds), false);
						}
					} else {
						for(TagKey<Block> tag : blockTagSoils.keySet()) {
							List<ResourceLocation> blockIds = blockTagSoils.get(tag).stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
							soilBuilder.add(tag, SoilInfo.of(blockIds), false);
						}
						for(ResourceLocation block : blockSoils.keySet()) {
							List<ResourceLocation> blockIds = blockSoils.get(block).stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
							soilBuilder.add(block, SoilInfo.of(blockIds), false);
						}
					}
				}
			}
		});
	}

	public static BonsaiInfo with(BonsaiInfo target, BonsaiGenerationInfo genInfo) {
		var newValidSoils = genInfo.validSoils().isEmpty() ? target.validSoils() : genInfo.validSoils();
		var newRequiredTicks = genInfo.requiredTicks().isEmpty() ? target.requiredTicks() : genInfo.requiredTicks();
		var newLightEmission = genInfo.lightEmission().isEmpty() ? target.lightEmission() : genInfo.lightEmission();
		return new BonsaiInfo(target.model(), newValidSoils, newRequiredTicks, newLightEmission);
	}
}
