package com.davenonymous.bonsaigen.lib.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public class SpawnHelper {

	public static boolean checkLoaded(ServerLevel level, ChunkPos start, ChunkPos end) {
		return ChunkPos.rangeClosed(start, end)
			.filter((chunkPos) -> !level.isLoaded(chunkPos.getWorldPosition()))
			.findAny()
			.isEmpty();
	}
}
