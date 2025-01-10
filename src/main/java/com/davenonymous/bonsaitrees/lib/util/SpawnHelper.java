package com.davenonymous.bonsaitrees.lib.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class SpawnHelper {
	public static void spawnItemStack(ItemStack stack, Level level, BlockPos pos) {
		if(!level.isClientSide()) {
			ItemEntity entity = new ItemEntity(level, (double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5, stack);
			entity.setDeltaMovement(0.0, 0.15, 0.0);
			entity.setPickUpDelay(10);
			entity.setExtendedLifetime();
			level.addFreshEntity(entity);
		}
	}

	public static void dropItemHandlerContents(IItemHandlerModifiable handler, Level level, BlockPos pos, boolean removeFromHandler) {
		if(!level.isClientSide()) {
			for(int slot = 0; slot < handler.getSlots(); ++slot) {
				ItemStack stack = handler.getStackInSlot(slot);
				if(!stack.isEmpty()) {
					spawnItemStack(stack, level, pos);
					if(removeFromHandler) {
						handler.setStackInSlot(slot, ItemStack.EMPTY);
					}
				}
			}

		}
	}

	public static void dropItemHandlerContents(IItemHandlerModifiable handler, Level level, BlockPos pos) {
		dropItemHandlerContents(handler, level, pos, false);
	}

	public static boolean checkLoaded(ServerLevel level, ChunkPos start, ChunkPos end) {
		return ChunkPos.rangeClosed(start, end)
			.filter((chunkPos) -> !level.isLoaded(chunkPos.getWorldPosition()))
			.findAny()
			.isEmpty();
	}
}