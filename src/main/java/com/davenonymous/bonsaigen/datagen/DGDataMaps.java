package com.davenonymous.bonsaigen.datagen;

import com.davenonymous.bonsaigen.BonsaiGen;
import com.davenonymous.bonsaigen.client.multiblock.MultiBlockFromFeatureGenerator;
import com.davenonymous.bonsaigen.multiblock.MultiBlockGeometryBase;
import com.davenonymous.bonsaigen.setup.ModDataMaps;
import com.davenonymous.bonsaigen.setup.cache.BonsaiGenerationCache;
import com.davenonymous.bonsaigen.setup.data.BonsaiGenerationInfo;
import com.davenonymous.bonsaigen.setup.data.BonsaiInfo;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.HugeFungusFeature;
import net.neoforged.neoforge.common.data.DataMapProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DGDataMaps extends DataMapProvider {
	public final String modId;
	public final DGTreeModelProvider treeModelProvider;

	public DGDataMaps(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider, String modId, DGTreeModelProvider treeModelProvider) {
		super(packOutput, lookupProvider);
		this.modId = modId;
		this.treeModelProvider = treeModelProvider;
	}

	public static final ResourceLocation _MYCELIUM = BonsaiGen.resource("mycelium");
	public static final ResourceLocation _NYLIUM = BonsaiGen.resource("nylium");
	private final Map<ResourceLocation, BonsaiInfo> bonsaiInfoMap = new HashMap<>();

	public BonsaiGenerationInfo getBonsaiGenerationInfo(ItemLike stack) {
		return BonsaiGenerationCache.BONSAI_GENERATION.containsKey(stack.asItem())
			   ? BonsaiGenerationCache.BONSAI_GENERATION.get(stack.asItem())
			   : BonsaiGenerationInfo.EMPTY();
	}

	public DGDataMaps addBonsai(ItemLike item, BonsaiInfo info) {
		ResourceLocation itemID = item.asItem().builtInRegistryHolder().getKey().location();
		bonsaiInfoMap.put(itemID, info);
		return this;
	}

	@Override
	protected void gather() {
		lookupProvider.thenAccept(provider -> {
			var builder = builder(ModDataMaps.BONSAI);
			treeModelProvider.itemToModel.forEach((item, model) -> {
				MultiBlockFromFeatureGenerator.Result floodfillResult = treeModelProvider.models.get(model);
				Feature<?> feature = floodfillResult.feature().feature();
				List<ResourceLocation> validSoils = new ArrayList<>();
				if(feature instanceof AbstractHugeMushroomFeature) {
					validSoils.add(_MYCELIUM);
				} else if(feature instanceof HugeFungusFeature) {
					validSoils.add(_NYLIUM);
				}

				MultiBlockGeometryBase geometry = floodfillResult.geometry();
				int lightEmission = geometry.lightEmission();
				Item bonsaiItem = provider.lookupOrThrow(Registries.ITEM).get(ResourceKey.create(Registries.ITEM, item)).get().value();
				BonsaiGenerationInfo generationInfo = getBonsaiGenerationInfo(bonsaiItem);
				if(item.getNamespace().equals(modId)) {
					if(bonsaiInfoMap.containsKey(item)) {
						BonsaiInfo info = bonsaiInfoMap.get(item).withModel(model).withLightEmission(lightEmission).with(generationInfo);
						if(info.validSoils().isEmpty()) {
							info = info.withValidSoils(validSoils);
						}
						builder.add(item, info, false);
					} else {
						builder.add(item, BonsaiInfo.plain(model).withLightEmission(lightEmission).withValidSoils(validSoils).with(generationInfo), false);
					}
				}
			});

		});
	}
}
