package com.davenonymous.bonsaitrees.lib;

import com.davenonymous.bonsaitrees.setup.data.ModelGenerationInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.apache.logging.log4j.util.BiConsumer;

import java.util.Optional;

public class Helpers {

	public static <T> Optional<T> firstNonEmpty(Optional<T>... optionals) {
		for(Optional<T> optional : optionals) {
			if(optional.isPresent()) {
				return optional;
			}
		}
		return Optional.empty();
	}

	public static ResourceKey<ConfiguredFeature<?, ?>> getTreeFeature(TreeGrower grower, ModelGenerationInfo options) {
		ResourceKey<ConfiguredFeature<?, ?>> tree = null;
		if(options.preferredFeature().isPresent()) {
			ModelGenerationInfo.TreeFeature feature = options.preferredFeature().get();
			tree = switch(feature) {
				case Tree -> grower.tree.orElse(null);
				case SecondaryTree -> grower.secondaryTree.orElse(null);
				case MegaTree -> grower.megaTree.orElse(null);
				case SecondaryMegaTree -> grower.secondaryMegaTree.orElse(null);
				case Flowers -> grower.flowers.orElse(null);
				case SecondaryFlowers -> grower.secondaryFlowers.orElse(null);
			};
		}

		if(tree == null) {
			tree = firstNonEmpty(
				grower.tree,
				grower.secondaryTree,
				grower.megaTree,
				grower.secondaryMegaTree,
				grower.flowers,
				grower.secondaryFlowers
			).orElse(null);
		}

		return tree;
	}

	public static void forAllFungusBlocks(BiConsumer<ResourceKey<ConfiguredFeature<?, ?>>, Item> consumer) {
		BuiltInRegistries.BLOCK.forEach(block -> {
			if(block instanceof FungusBlock fungus) {
				ResourceKey<ConfiguredFeature<?, ?>> feature = fungus.feature;
				if(feature == null) {
					return;
				}
				consumer.accept(feature, block.asItem());
			}
		});
	}

	public static void forAllMushroomBlocks(BiConsumer<ResourceKey<ConfiguredFeature<?, ?>>, Item> consumer) {
		BuiltInRegistries.BLOCK.forEach(block -> {
			if(block instanceof MushroomBlock mushroom) {
				ResourceKey<ConfiguredFeature<?, ?>> feature = mushroom.feature;
				if(feature == null) {
					return;
				}
				consumer.accept(feature, block.asItem());
			}
		});
	}

	public static void forAllSaplingBlocks(BiConsumer<TreeGrower, Item> consumer) {
		BuiltInRegistries.BLOCK.forEach(block -> {
			TreeGrower grower = null;
			if(block instanceof SaplingBlock sapling) {
				grower = sapling.treeGrower;
			}

			if(block instanceof AzaleaBlock azalea) {
				grower = TreeGrower.AZALEA;
			}

			if(grower == null) {
				return;
			}

			Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree = firstNonEmpty(
				grower.tree, grower.secondaryTree, grower.megaTree, grower.secondaryMegaTree, grower.flowers, grower.secondaryFlowers);
			if(tree.isEmpty()) {
				return;
			}

			consumer.accept(grower, block.asItem());
		});
	}
}
