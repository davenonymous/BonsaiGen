package com.davenonymous.bonsaigen.client.multiblock;

import com.davenonymous.bonsaigen.multiblock.FloodFill;
import com.davenonymous.bonsaigen.setup.config.PackGenConfig;
import com.davenonymous.bonsaigen.setup.data.ModelGenerationInfo;
import com.davenonymous.bonsaitrees.multiblock.MultiBlockGeometryBase;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.HugeFungusConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

import java.util.*;

public class MultiBlockFromFeatureGenerator {
	public static boolean GENERATING_MODELS = false;
	private final ServerLevel level;
	private final RegistryAccess registryAccess;
	private final Registry<ConfiguredFeature<?, ?>> configuredFeatureRegistry;
	private final Registry<Feature<?>> featureRegistry;
	private ChunkPos centerChunk;
	private BlockPos placePosition;
	private BlockState floor;

	public MultiBlockFromFeatureGenerator(ServerLevel level) {
		this.level = level;
		this.registryAccess = level.registryAccess();
		this.configuredFeatureRegistry = registryAccess.registryOrThrow(Registries.CONFIGURED_FEATURE);
		this.featureRegistry = registryAccess.registryOrThrow(Registries.FEATURE);
		this.floor = Blocks.DIRT.defaultBlockState();
		this.setChunk(ChunkPos.ZERO);
	}

	public MultiBlockFromFeatureGenerator setChunk(ChunkPos chunkPos) {
		this.centerChunk = chunkPos;
		this.placePosition = new BlockPos(chunkPos.getMiddleBlockX(), level.getMaxBuildHeight() - 128, chunkPos.getMiddleBlockZ());
		return this;
	}

	public MultiBlockFromFeatureGenerator setFloor(BlockState floor) {
		this.floor = floor;
		return this;
	}

	public <FC extends FeatureConfiguration, F extends Feature<FC>> Result fromFeature(F feature, FC featureConfig, ModelGenerationInfo options) {
		var configuredFeature = new ConfiguredFeature<>(feature, featureConfig);
		ResourceLocation featureId = featureRegistry.getKey(feature);
		return fromFeature(configuredFeature, featureId, options);
	}

	public Result fromFeature(ConfiguredFeature<?, ?> feature, ResourceLocation featureId, ModelGenerationInfo options) {
		var result = new Result.Builder();
		result.feature(feature);
		result.featureId(featureId);
		result.info("Generating multi-block from feature: " + featureId);
		result.pushIndent();
		resetArena(feature, options, Block.UPDATE_NONE);

		result.info("Custom options: " + (options.isEmpty() ? "None" : "Yes"));

		long seed = options.fixedSeed().orElse((long) featureId.hashCode());
		result.info("Using seed: " + seed);

		int yCutOff = options.yOffset().orElse(0);
		result.info("Using yCutOff: " + yCutOff);

		WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(seed));
		RandomSource oldRandom = level.random;
		level.random = worldgenrandom;

		GENERATING_MODELS = true;
		var actualPlacePosition = placePosition.above();
		boolean success = false;
		for(int yOffset = 0; yOffset < 4; yOffset++) {
			success = feature.place(level, level.getChunkSource().getGenerator(), worldgenrandom, actualPlacePosition);
			if(success) {
				break;
			}
			actualPlacePosition = actualPlacePosition.above();
		}
		GENERATING_MODELS = false;

		if(!success) {
			resetArena(feature, options, Block.UPDATE_NONE);
			level.random = oldRandom;
			result.error("Failed to place feature");
			return result.build();
		}

		// Determine a position to start the flood fill from
		// This is either the position above the sapling or the position returned by the root placer
		var rootPos = placePosition.above();
		if(level.getBlockState(rootPos).isAir()) {
			for(var neighborPos : BlockPos.spiralAround(rootPos, 16, Direction.EAST, Direction.NORTH)) {
				if(!level.getBlockState(neighborPos).isAir()) {
					rootPos = neighborPos;
					break;
				}
			}
		}

		if(level.getBlockState(rootPos).isAir()) {
			resetArena(feature, options, Block.UPDATE_NONE);
			level.random = oldRandom;
			result.error("Could not determine root position");
			return result.build();
		}

		// Perform a flood fill to determine the connected blocks
		FloodFill floodFill = new FloodFill(level, rootPos);
		Map<BlockPos, BlockState> connectedBlocks = floodFill.getConnectedBlocks(yCutOff);

		if(connectedBlocks.isEmpty()) {
			resetArena(feature, options, Block.UPDATE_NONE);
			level.random = oldRandom;
			result.error("Flood fill returned no connected blocks");
			return result.build();
		}

		// Trim the trunks from the connected blocks
		if(PackGenConfig.trimTrunks) {
			int trimCount = connectedBlocks.size();
			connectedBlocks = trimTrunks(connectedBlocks, PackGenConfig.trimTrunkThreshold);
			trimCount -= connectedBlocks.size();
			if(trimCount > 0) {
				result.info("Trimmed " + trimCount + " trunk blocks");
			}
		}

		int lightEmission = getLightEmission(connectedBlocks, level);

		clearArena(Block.UPDATE_NONE);
		level.random = oldRandom;
		result.info("Placed " + connectedBlocks.keySet().size() + " blocks with " + new HashSet<>(connectedBlocks.values()).size() + " block states");
		MultiBlockGeometryBase geometry = MultiBlockGeometryBase.forDataGen(connectedBlocks, lightEmission);
		result.geometry(geometry);
		return result.build();
	}

	public Result fromFeature(ResourceKey<ConfiguredFeature<?, ?>> featureKey, ModelGenerationInfo options) {
		Holder.Reference<ConfiguredFeature<?, ?>> featureHolder = configuredFeatureRegistry.getHolderOrThrow(featureKey);
		ConfiguredFeature<?, ? extends Feature<?>> feature = featureHolder.value();
		ResourceLocation featureId = featureKey.location();

		return fromFeature(feature, featureId, options);
	}

	private void clearArena(int updateFlags) {
		int yPos = level.getMaxBuildHeight() - 132;
		ChunkPos.rangeClosed(centerChunk, 1).forEach(chunkPos -> {
			BlockPos.betweenClosedStream(chunkPos.getMinBlockX(), yPos, chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), level.getMaxBuildHeight(), chunkPos.getMaxBlockZ())
				.forEach(pos -> {
					BlockState airState = Blocks.AIR.defaultBlockState();
					if(pos.getY() == yPos) {
						level.setBlock(pos.below(), airState, updateFlags);
					}

					level.setBlock(pos, airState, updateFlags);
				});
		});
	}

	private void resetArena(ConfiguredFeature<?, ?> feature, ModelGenerationInfo options, int updateFlags) {
		BlockState bestFloor = options.preferredSoil().orElse(floor);
		FeatureConfiguration config = feature.config();
		if(config instanceof HugeFungusConfiguration fungusConfiguration) {
			if(fungusConfiguration.validBaseState != null) {
				bestFloor = fungusConfiguration.validBaseState;
			}
		} else if(config instanceof TreeConfiguration treeConfiguration) {
			if(treeConfiguration.dirtProvider != null) {
				bestFloor = treeConfiguration.dirtProvider.getState(level.random, placePosition.below());
			}
		}

		BlockState floorState = bestFloor;
		int yPos = level.getMaxBuildHeight() - 128;
		ChunkPos.rangeClosed(centerChunk, 1).forEach(chunkPos -> {
			BlockPos.betweenClosedStream(chunkPos.getMinBlockX(), yPos, chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), level.getMaxBuildHeight(), chunkPos.getMaxBlockZ())
				.forEach(pos -> {
					BlockState airState = options.preferredMedium().orElse(Blocks.AIR.defaultBlockState());
					if(pos.getY() == yPos) {
						level.setBlock(pos, floorState, updateFlags);
						level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), updateFlags);
					} else {
						level.setBlock(pos, airState, updateFlags);
					}
				});
		});
	}


	public static Map<BlockPos, BlockState> removeSlice(Map<BlockPos, BlockState> blocks, int y) {
		var newBlocks = new HashMap<BlockPos, BlockState>();
		for(var entry : blocks.entrySet()) {
			BlockPos pos = entry.getKey();
			if(pos.getY() != y) {
				if(pos.getY() > y) {
					newBlocks.put(pos.below(), entry.getValue());
				} else {
					newBlocks.put(pos, entry.getValue());
				}
			}
		}

		return newBlocks;
	}

	public static Map<Integer, List<BlockPos>> getSliceBlocks(Map<BlockPos, BlockState> blocks) {
		Map<Integer, List<BlockPos>> sliceBlocks = new HashMap<>();
		for(var entry : blocks.entrySet()) {
			if(entry.getValue().isAir()) {
				continue;
			}

			BlockPos pos = entry.getKey();
			if(!sliceBlocks.containsKey(pos.getY())) {
				sliceBlocks.put(pos.getY(), new ArrayList<>());
			}
			sliceBlocks.get(pos.getY()).add(pos);
		}
		return sliceBlocks;
	}

	public static int getLightEmission(Map<BlockPos, BlockState> blocks, ServerLevel level) {
		int lightEmission = 0;
		for(var entry : blocks.entrySet()) {
			BlockState state = entry.getValue();
			lightEmission = Math.max(state.getLightEmission(level, entry.getKey()), lightEmission);
		}

		return lightEmission;
	}

	public static Map<BlockPos, BlockState> trimTrunks(Map<BlockPos, BlockState> blocks, int trunkTreshold) {

		int maxChecks = blocks.keySet().stream().map(BlockPos::getY).max(Integer::compareTo).orElse(0);
		for(int y = 0; y < maxChecks; y++) {
			List<BlockPos> sliceBlocks = getSliceBlocks(blocks).get(y);
			if(sliceBlocks == null || sliceBlocks.isEmpty()) {
				break;
			}

			if(sliceBlocks.size() > 4) {
				break;
			}

			List<BlockPos> nextSliceBlocks = getSliceBlocks(blocks).get(y + 1);
			if(nextSliceBlocks == null || nextSliceBlocks.isEmpty()) {
				break;
			}

			if(sliceBlocks.size() != nextSliceBlocks.size()) {
				continue;
			}

			boolean allMatch = true;
			SliceLoop:
			for(BlockPos testPos : sliceBlocks) {
				BlockState testState = blocks.get(testPos);
				for(int testY = 0; testY < trunkTreshold; testY++) {
					BlockState aboveState = blocks.get(testPos.above(testY + 1));
					if(aboveState != null && !testState.equals(aboveState)) {
						allMatch = false;
						break SliceLoop;
					}
				}
			}

			if(allMatch) {
				blocks = removeSlice(blocks, y);
				y--;
			}
		}

		return blocks;
	}

	public record Result(ResourceLocation featureId, ConfiguredFeature<?, ?> feature, MultiBlockGeometryBase geometry, List<Component> messages) {

		public static class Builder {
			private ResourceLocation featureId;
			private ConfiguredFeature<?, ?> feature;
			private MultiBlockGeometryBase geometry;
			private List<Component> messages = new LinkedList<>();
			private int indentDepth = 0;

			public Builder featureId(ResourceLocation featureId) {
				this.featureId = featureId;
				return this;
			}

			public Builder feature(ConfiguredFeature<?, ?> feature) {
				this.feature = feature;
				return this;
			}

			public Builder geometry(MultiBlockGeometryBase geometry) {
				this.geometry = geometry;
				return this;
			}

			public Builder pushIndent() {
				indentDepth++;
				return this;
			}

			public Builder popIndent() {
				indentDepth--;
				return this;
			}

			public Builder info(String message) {
				String indent = "  ".repeat(indentDepth);
				messages.add(Component.literal(indent + message));
				return this;
			}

			public Builder warn(String message) {
				String indent = "  ".repeat(indentDepth);
				messages.add(Component.literal(indent + message).withColor(0xffaa00));
				return this;
			}

			public Builder error(String message) {
				String indent = "  ".repeat(indentDepth);
				messages.add(Component.literal(indent + message).withColor(0xff0000));
				return this;
			}

			public Builder messages(List<Component> messages) {
				this.messages = messages;
				return this;
			}

			public Result build() {
				return new Result(featureId, feature, geometry, messages);
			}
		}
	}

}
