package com.davenonymous.bonsaitrees.setup.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Represents the generation information for a model.
 * These are loaded from the data pack and are used to determine how a model is generated.
 * The backing data map is {@link com.davenonymous.bonsaitrees.setup.ModDataMaps#FIXED_TREE_GENERATION_SEEDS}
 *
 * @param fixedSeed
 * @param yOffset
 * @param preferredFeature
 */
public record ModelGenerationInfo(
	Optional<Long> fixedSeed,
	Optional<Integer> yOffset,
	Optional<TreeFeature> preferredFeature,
	Optional<BlockState> preferredMedium,
	Optional<BlockState> preferredSoil
)
{
	public enum TreeFeature implements StringRepresentable {
		Tree,
		SecondaryTree,
		MegaTree,
		SecondaryMegaTree,
		Flowers,
		SecondaryFlowers;

		public static final StringRepresentable.EnumCodec<TreeFeature> CODEC = StringRepresentable.fromEnum(TreeFeature::values);

		@Override
		public String getSerializedName() {
			return this.name();
		}
	}

	public ModelGenerationInfo setAquatic() {
		return new ModelGenerationInfo(fixedSeed, yOffset, preferredFeature, Optional.of(Blocks.WATER.defaultBlockState()), Optional.of(Blocks.SAND.defaultBlockState()));
	}

	public ModelGenerationInfo withSeed(long seed) {
		return new ModelGenerationInfo(Optional.of(seed), yOffset, preferredFeature, preferredMedium, preferredSoil);
	}

	public ModelGenerationInfo withYOffset(int y) {
		return new ModelGenerationInfo(fixedSeed, Optional.of(y), preferredFeature, preferredMedium, preferredSoil);
	}

	public ModelGenerationInfo withPreferredFeature(TreeFeature feature) {
		return new ModelGenerationInfo(fixedSeed, yOffset, Optional.of(feature), preferredMedium, preferredSoil);
	}

	public ModelGenerationInfo withPreferredMedium(BlockState medium) {
		return new ModelGenerationInfo(fixedSeed, yOffset, preferredFeature, Optional.of(medium), preferredSoil);
	}

	public ModelGenerationInfo withPreferredSoil(BlockState soil) {
		return new ModelGenerationInfo(fixedSeed, yOffset, preferredFeature, preferredMedium, Optional.of(soil));
	}

	public static ModelGenerationInfo EMPTY() {
		return new ModelGenerationInfo(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}

	public static final MapCodec<ModelGenerationInfo> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
		return instance.group(
			Codec.LONG.optionalFieldOf("seed").forGetter(ModelGenerationInfo::fixedSeed),
			Codec.INT.optionalFieldOf("yCutOff").forGetter(ModelGenerationInfo::yOffset),
			TreeFeature.CODEC.optionalFieldOf("preferredFeature").forGetter(ModelGenerationInfo::preferredFeature),
			BlockState.CODEC.optionalFieldOf("preferredMedium").forGetter(ModelGenerationInfo::preferredMedium),
			BlockState.CODEC.optionalFieldOf("preferredSoil").forGetter(ModelGenerationInfo::preferredSoil)
		).apply(instance, ModelGenerationInfo::new);
	});
}