package com.davenonymous.bonsaigen.command.generate;

import com.davenonymous.bonsaigen.command.arguments.ModOrAllArgument;
import com.davenonymous.bonsaigen.datagen.DGDataMaps;
import com.davenonymous.bonsaigen.datagen.DGDataRegistries;
import com.davenonymous.bonsaigen.datagen.DGSaplingLootBuilder;
import com.davenonymous.bonsaigen.datagen.DGTreeModelProvider;
import com.davenonymous.bonsaigen.lib.FileCopyGenerator;
import com.davenonymous.bonsaigen.lib.ResourcePackMetadataGenerator;
import com.davenonymous.bonsaigen.lib.util.SpawnHelper;
import com.davenonymous.bonsaigen.lib.util.ZippingFileVisitor;
import com.davenonymous.bonsaigen.setup.config.PackGenConfig;
import com.davenonymous.bonsaigen.setup.data.BonsaiInfo;
import com.davenonymous.bonsaigen.setup.data.ModelGenerationInfo;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.DetectedVersion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.metadata.PackMetadataGenerator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.ZipOutputStream;

public class GenerateDataPack implements Command<CommandSourceStack> {
	private static final GenerateDataPack INSTANCE = new GenerateDataPack();

	private GenerateDataPack() {
	}

	public static ArgumentBuilder<CommandSourceStack, ?> register(CommandDispatcher<CommandSourceStack> dispatcher) {
		return Commands
			.argument("mod", ModOrAllArgument.modOrAllArgument())
			.executes(INSTANCE);
	}

	private ChunkPos getAimedChunk(ServerPlayer player) throws CommandSyntaxException {
		var level = player.serverLevel();
		var realChunkPos = player.chunkPosition();
		var lookDirection = player.getNearestViewDirection();
		ChunkPos chunkPos;
		switch(lookDirection) {
			case NORTH:
				chunkPos = new ChunkPos(realChunkPos.x, realChunkPos.z - 2);
				break;
			case SOUTH:
				chunkPos = new ChunkPos(realChunkPos.x, realChunkPos.z + 2);
				break;
			case EAST:
				chunkPos = new ChunkPos(realChunkPos.x + 2, realChunkPos.z);
				break;
			case WEST:
				chunkPos = new ChunkPos(realChunkPos.x - 2, realChunkPos.z);
				break;
			default:
				throw new CommandSyntaxException(
					CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException(),
					() -> "Could not determine look direction. Please look somewhere else"
				);
		}

		if(!SpawnHelper.checkLoaded(level, new ChunkPos(chunkPos.x - 1, chunkPos.z - 1), new ChunkPos(chunkPos.x + 1, chunkPos.z + 1))) {
			throw new CommandSyntaxException(
				CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException(),
				() -> "Chunks in that direction are not loaded. This should not happen. Report an issue please."
			);
		}

		return chunkPos;
	}

	public static boolean generateDataPack(String inputModId, ServerLevel level, ChunkPos chunkPos, Consumer<Component> onMessage) {
		List<String> toGenerate = new ArrayList<>();
		if(inputModId.equals("--all")) {
			ModList.get().applyForEachModContainer(ModContainer::getModId).forEach(modId -> {
				if(PackGenConfig.modBlacklist.contains(modId)) {
					return;
				}
				toGenerate.add(modId);
			});
		} else {
			if(!ModList.get().applyForEachModContainer(ModContainer::getModId).anyMatch(inputModId::equals)) {
				onMessage.accept(Component.literal("Mod not found: " + inputModId));
				return false;
			}

			toGenerate.add(inputModId);
		}

		var registryAccess = level.registryAccess();
		var registryFuture = CompletableFuture.completedFuture((HolderLookup.Provider) registryAccess);
		var existingFileHelper = new ExistingFileHelper(Collections.emptySet(), Collections.emptySet(), true, null, null);

		var basePath = Path.of(PackGenConfig.path);
		for(String modId : toGenerate) {
			onMessage.accept(Component.literal("Starting data-pack generation for: " + modId));

			var dataPath = basePath.resolve("datapacks").resolve(modId);
			var resourcePath = basePath.resolve("resourcepacks").resolve(modId);
			var dataGen = new DataGenerator(dataPath, DetectedVersion.BUILT_IN, true);
			var resourceGen = new DataGenerator(resourcePath, DetectedVersion.BUILT_IN, true);

			var dataOutput = dataGen.getPackOutput();
			var resourceOutput = resourceGen.getPackOutput();

			var treeModelProvider = new DGTreeModelProvider(resourceOutput, existingFileHelper, level, registryAccess, chunkPos);
			treeModelProvider
				.gatherTrees(modId, onMessage)
				.gatherFunghi(modId, onMessage);

			if(modId.equals("minecraft")) {
				treeModelProvider.addFeature(
					Items.BRAIN_CORAL, Feature.CORAL_CLAW, FeatureConfiguration.NONE,
					ModelGenerationInfo.EMPTY().setAquatic(),
					onMessage
				);
				treeModelProvider.addFeature(
					Items.BUBBLE_CORAL, Feature.CORAL_MUSHROOM, FeatureConfiguration.NONE,
					ModelGenerationInfo.EMPTY().setAquatic(),
					onMessage
				);
				treeModelProvider.addFeature(
					Items.HORN_CORAL, Feature.CORAL_TREE, FeatureConfiguration.NONE,
					ModelGenerationInfo.EMPTY().setAquatic(),
					onMessage
				);
			}

			if(treeModelProvider.count() == 0) {
				continue;
			}


			var lootBuilder = DGSaplingLootBuilder.forMod(modId, treeModelProvider.models);
			var lootProvider = new LootTableProvider(dataOutput, Collections.emptySet(), List.of(lootBuilder), registryFuture);
			var dataMapProvider = new DGDataMaps(dataOutput, registryFuture, modId, treeModelProvider);
			if(modId.equals("minecraft")) {
				dataMapProvider.addBonsai(Items.BRAIN_CORAL, BonsaiInfo.of(DGDataRegistries.WATER.location()));
				dataMapProvider.addBonsai(Items.BUBBLE_CORAL, BonsaiInfo.of(DGDataRegistries.WATER.location()));
				dataMapProvider.addBonsai(Items.HORN_CORAL, BonsaiInfo.of(DGDataRegistries.WATER.location()));
				dataMapProvider.addBonsai(Items.CRIMSON_FUNGUS, BonsaiInfo.plain(null));
				dataMapProvider.addBonsai(Items.WARPED_FUNGUS, BonsaiInfo.plain(null));
			}

			var dataMetaProvider = PackMetadataGenerator.forFeaturePack(dataOutput, Component.literal("Bonsai support for: " + modId));

			Path pngSource = Path.of("bonsaitrees_pack_logo.png");
			Path pngTarget = Path.of("pack.png");
			var dataPngProvider = new FileCopyGenerator(dataOutput).add(pngSource, pngTarget);

			dataGen.addProvider(true, lootProvider);
			dataGen.addProvider(true, dataMapProvider);
			dataGen.addProvider(true, dataMetaProvider);
			dataGen.addProvider(true, dataPngProvider);


			var resourceMetaProvider = ResourcePackMetadataGenerator.forResourcePack(resourceOutput, Component.literal("Bonsai models for: " + modId));
			var resourcePngProvider = new FileCopyGenerator(resourceOutput).add(pngSource, pngTarget);
			resourceGen.addProvider(true, resourceMetaProvider);
			resourceGen.addProvider(true, resourcePngProvider);
			resourceGen.addProvider(true, treeModelProvider);

			boolean didDataGen = false;
			try {
				dataGen.run();
				didDataGen = true;
			} catch (IOException e) {
				onMessage.accept(Component.literal("Failed to run data gen: " + e.getMessage()));
			}

			boolean didResourceGen = false;
			try {
				resourceGen.run();
				didResourceGen = true;
			} catch (IOException e) {
				onMessage.accept(Component.literal("Failed to run resource gen: " + e.getMessage()));
			}

			if(PackGenConfig.removeCache) {
				LinkedList<Path> toDelete = new LinkedList<>();
				toDelete.add(dataPath.resolve(".cache"));
				toDelete.add(resourcePath.resolve(".cache"));

				try {
					while(!toDelete.isEmpty()) {
						Path current = toDelete.peekFirst();
						if(Files.isDirectory(current) && !Files.list(current).toList().isEmpty()) {
							Files.walk(current).forEach(toDelete::addFirst);
						} else {
							Files.deleteIfExists(current);
							toDelete.pollFirst();
						}
					}
				} catch (IOException e) {
					onMessage.accept(Component.literal("Failed to delete cache: " + e.getMessage()));
				}
			}

			if(PackGenConfig.createZip) {
				if(didResourceGen) {
					try {
						var fileOutput = new FileOutputStream(basePath.resolve("bonsai-models-for-" + modId + ".zip")
							.toFile());
						var zipOutput = new ZipOutputStream(fileOutput);
						Files.walkFileTree(
							resourcePath.resolve("assets"),
							new ZippingFileVisitor(resourcePath, zipOutput)
						);
						ZippingFileVisitor.createZipEntryFromFile(
							resourcePath.resolve("pack.mcmeta"),
							"pack.mcmeta",
							zipOutput
						);
						zipOutput.close();
					} catch (IOException e) {
						onMessage.accept(Component.literal("Failed to create resource pack zip: " + e.getMessage()));
						return false;
					}
				}

				if(didDataGen) {
					try {
						var fileOutput = new FileOutputStream(basePath.resolve("bonsai-data-for-" + modId + ".zip")
							.toFile());
						var zipOutput = new ZipOutputStream(fileOutput);
						Files.walkFileTree(dataPath.resolve("data"), new ZippingFileVisitor(dataPath, zipOutput));
						ZippingFileVisitor.createZipEntryFromFile(
							dataPath.resolve("pack.mcmeta"),
							"pack.mcmeta",
							zipOutput
						);
						zipOutput.close();
					} catch (IOException e) {
						onMessage.accept(Component.literal("Failed to create data pack zip: " + e.getMessage()));
						return false;
					}
				}
			}
		}

		onMessage.accept(Component.literal("Successfully generated data packs for " + toGenerate.size() + " mods"));
		return true;
	}

	@SuppressWarnings("ConstantConditions")
	@Override
	public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String inputModId = context.getArgument("mod", String.class);

		boolean success = generateDataPack(
			inputModId, context.getSource().getLevel(), getAimedChunk(context.getSource().getPlayer()), component -> {
				context.getSource().sendSuccess(() -> component, false);
			}
		);

		return success ? 1 : 0;
	}


}
