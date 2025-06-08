package com.davenonymous.bonsaigen.multiblock;


import com.davenonymous.bonsaitrees.multiblock.MultiBlockGeometryBase;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FloodFill {
	private List<Block> allowedBlocks;
	private final List<Block> ignoredBlocks = Arrays.asList(
		Blocks.DIRT, Blocks.ROOTED_DIRT, Blocks.STONE, Blocks.COBBLESTONE, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Blocks.GRAVEL, Blocks.SAND, Blocks.SANDSTONE, Blocks.WATER,
		Blocks.BEDROCK, Blocks.WARPED_NYLIUM, Blocks.CRIMSON_NYLIUM,
		Blocks.END_STONE
	);
	private final int MAX_SEARCH_DEPTH = 2048;
	private final int MAX_BLOCKS = 4196;

	private final LevelReader world;
	private final BlockPos startingPosition;
	private Map<BlockPos, BlockState> result;

	public FloodFill(LevelReader world, BlockPos startingPosition) {
		this.world = world;
		this.startingPosition = startingPosition;
	}

	public FloodFill(LevelReader world, BlockPos startingPos, Block... allowedBlocks) {
		this(world, startingPos);
		this.allowedBlocks = List.of(allowedBlocks);
	}

	public FloodFill(LevelReader world, BlockPos startingPos, List allowedBlocks) {
		this(world, startingPos);
		this.allowedBlocks = allowedBlocks;
	}

	public static MultiBlockGeometryBase floodfill(LevelReader world, BlockPos pos) {
		FloodFill floodFill = new FloodFill(world, pos);
		Map<BlockPos, MultiBlockGeometryBase.Voxel> blocks = MultiBlockGeometryBase.castVoxelMap(floodFill.getConnectedBlocks());
		return new MultiBlockGeometryBase(1, 4, blocks, 0);
	}

	public static Map<BlockPos, BlockState> normalizeBlockPosMap(Map<BlockPos, BlockState> input) {
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int minX = Integer.MAX_VALUE;
		for(BlockPos pos : input.keySet()) {
			if(pos.getY() < minY) {
				minY = pos.getY();
			}
			if(pos.getZ() < minZ) {
				minZ = pos.getZ();
			}
			if(pos.getX() < minX) {
				minX = pos.getX();
			}
		}

		Map<BlockPos, BlockState> result = new HashMap<>();
		for(Map.Entry<BlockPos, BlockState> blockInfo : input.entrySet()) {
			result.put(blockInfo.getKey().offset(-minX, -minY, -minZ), blockInfo.getValue());
		}

		return result;
	}

	public Map<BlockPos, BlockState> getConnectedBlocks(boolean normalize, int yCutOff) {
		result = new HashMap<>();
		floodFill(world, startingPosition, 0);
		if(yCutOff > 0) {
			result = normalizeBlockPosMap(result);
			result.entrySet().removeIf(entry -> entry.getKey().getY() < yCutOff);
		}

		if(normalize) {
			return normalizeBlockPosMap(result);
		}

		return result;
	}

	public Map<BlockPos, BlockState> getConnectedBlocks(int yCutOff) {
		return getConnectedBlocks(true, yCutOff);
	}

	public Map<BlockPos, BlockState> getConnectedBlocks() {
		return getConnectedBlocks(true, 0);
	}

	private void floodFill(LevelReader world, BlockPos pos, int depth) {
		if(depth > MAX_SEARCH_DEPTH) {
			return;
		}

		if(result.size() > MAX_BLOCKS) {
			return;
		}

		if(result.containsKey(pos)) {
			return;
		}

		BlockState state = world.getBlockState(pos);
		if(state.isAir()) {
			return;
		}

		if(ignoredBlocks.contains(state.getBlock())) {
			return;
		}

		if(state.is(BlockTags.DIRT)) {
			return;
		}

		if(state.is(BlockTags.LUSH_GROUND_REPLACEABLE)) {
			return;
		}

		if(allowedBlocks != null && !allowedBlocks.contains(state.getBlock())) {
			return;
		}

		result.put(pos, state);

		for(int x = -1; x < 2; x++) {
			for(int y = -1; y < 2; y++) {
				for(int z = -1; z < 2; z++) {
					if(x == 0 && y == 0 && z == 0) {
						continue;
					}

					floodFill(world, pos.offset(x, y, z), depth + 1);
				}
			}
		}
	}
}
