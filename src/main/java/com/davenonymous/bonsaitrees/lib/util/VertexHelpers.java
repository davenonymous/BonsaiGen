package com.davenonymous.bonsaitrees.lib.util;

import net.minecraft.core.Direction;

import java.util.BitSet;

public class VertexHelpers {
	public static BitSet getShapeFlags(float[] shape, Direction side) {
		float f = shape[Direction.WEST.get3DDataValue()];
		float f1 = shape[Direction.DOWN.get3DDataValue()];
		float f2 = shape[Direction.NORTH.get3DDataValue()];
		float f3 = shape[Direction.EAST.get3DDataValue()];
		float f4 = shape[Direction.UP.get3DDataValue()];
		float f5 = shape[Direction.SOUTH.get3DDataValue()];

		BitSet shapeFlags = new BitSet(2);
		switch(side) {
			case DOWN:
				shapeFlags.set(1, f >= 1.0E-4F || f2 >= 1.0E-4F || f3 <= 0.9999F || f5 <= 0.9999F);
				shapeFlags.set(0, f1 == f4 && f1 < 1.0E-4F);
				break;
			case UP:
				shapeFlags.set(1, f >= 1.0E-4F || f2 >= 1.0E-4F || f3 <= 0.9999F || f5 <= 0.9999F);
				shapeFlags.set(0, f1 == f4 && f4 > 0.9999F);
				break;
			case NORTH:
				shapeFlags.set(1, f >= 1.0E-4F || f1 >= 1.0E-4F || f3 <= 0.9999F || f4 <= 0.9999F);
				shapeFlags.set(0, f2 == f5 && f2 < 1.0E-4F);
				break;
			case SOUTH:
				shapeFlags.set(1, f >= 1.0E-4F || f1 >= 1.0E-4F || f3 <= 0.9999F || f4 <= 0.9999F);
				shapeFlags.set(0, f2 == f5 && f5 > 0.9999F);
				break;
			case WEST:
				shapeFlags.set(1, f1 >= 1.0E-4F || f2 >= 1.0E-4F || f4 <= 0.9999F || f5 <= 0.9999F);
				shapeFlags.set(0, f == f3 && f < 1.0E-4F);
				break;
			case EAST:
				shapeFlags.set(1, f1 >= 1.0E-4F || f2 >= 1.0E-4F || f4 <= 0.9999F || f5 <= 0.9999F);
				shapeFlags.set(0, f == f3 && f3 > 0.9999F);
		}

		return shapeFlags;
	}

	public static float[] calculateShape(int[] vertices) {
		float f = 32.0F;
		float f1 = 32.0F;
		float f2 = 32.0F;
		float f3 = -32.0F;
		float f4 = -32.0F;
		float f5 = -32.0F;

		for(int j = 0; j < 4; ++j) {
			float f10 = Float.intBitsToFloat(vertices[j * 8]);
			float f7 = Float.intBitsToFloat(vertices[j * 8 + 1]);
			float f8 = Float.intBitsToFloat(vertices[j * 8 + 2]);
			f = Math.min(f, f10);
			f1 = Math.min(f1, f7);
			f2 = Math.min(f2, f8);
			f3 = Math.max(f3, f10);
			f4 = Math.max(f4, f7);
			f5 = Math.max(f5, f8);
		}

		float[] shape = new float[24];
		shape[Direction.WEST.get3DDataValue()] = f;
		shape[Direction.EAST.get3DDataValue()] = f3;
		shape[Direction.DOWN.get3DDataValue()] = f1;
		shape[Direction.UP.get3DDataValue()] = f4;
		shape[Direction.NORTH.get3DDataValue()] = f2;
		shape[Direction.SOUTH.get3DDataValue()] = f5;
		var offset = Direction.values().length;
		shape[Direction.WEST.get3DDataValue() + offset] = 1.0F - f;
		shape[Direction.EAST.get3DDataValue() + offset] = 1.0F - f3;
		shape[Direction.DOWN.get3DDataValue() + offset] = 1.0F - f1;
		shape[Direction.UP.get3DDataValue() + offset] = 1.0F - f4;
		shape[Direction.NORTH.get3DDataValue() + offset] = 1.0F - f2;
		shape[Direction.SOUTH.get3DDataValue() + offset] = 1.0F - f5;

		return shape;
	}
}