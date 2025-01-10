package com.davenonymous.bonsaitrees.datagen;

import com.davenonymous.bonsaitrees.BonsaiTrees;
import com.davenonymous.bonsaitrees.client.multiblock.MultiBlockFromFeatureGenerator;
import com.davenonymous.bonsaitrees.multiblock.MultiBlockGeometryBase;
import com.davenonymous.bonsaitrees.setup.ModDataMaps;
import com.davenonymous.bonsaitrees.setup.ModTags;
import com.davenonymous.bonsaitrees.setup.cache.BonsaiGenerationCache;
import com.davenonymous.bonsaitrees.setup.data.BonsaiGenerationInfo;
import com.davenonymous.bonsaitrees.setup.data.BonsaiInfo;
import com.davenonymous.bonsaitrees.setup.data.SoilInfo;
import com.davenonymous.bonsaitrees.setup.data.SoilInfoWithTexture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.HugeFungusFeature;
import net.neoforged.neoforge.common.Tags;
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

	public static final ResourceLocation _DIRT = BonsaiTrees.resource("dirt");
	public static final ResourceLocation _SAND = BonsaiTrees.resource("sand");
	public static final ResourceLocation _END_STONE = BonsaiTrees.resource("end_stone");
	public static final ResourceLocation _NETHER_STONE = BonsaiTrees.resource("nether_stone");
	public static final ResourceLocation _STONE = BonsaiTrees.resource("stone");
	public static final ResourceLocation _WATER = BonsaiTrees.resource("water");
	public static final ResourceLocation _LAVA = BonsaiTrees.resource("lava");
	public static final ResourceLocation _MYCELIUM = BonsaiTrees.resource("mycelium");
	public static final ResourceLocation _NYLIUM = BonsaiTrees.resource("nylium");

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
		});
	}
}
