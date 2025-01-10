package com.davenonymous.bonsaitrees.lib.util;

import net.minecraft.core.Direction;

public class DirectionUtils {
	public static Direction rotatedBlockSide(Direction blockFaceDirection, Direction accessSide) {
		var currentDirection = accessSide;
		int rotationsNeeded = (blockFaceDirection.get2DDataValue() + 2) % 4;
		for(int i = 0; i < rotationsNeeded; i++) {
			currentDirection = currentDirection.getCounterClockWise();
		}
		return currentDirection;
	}
}