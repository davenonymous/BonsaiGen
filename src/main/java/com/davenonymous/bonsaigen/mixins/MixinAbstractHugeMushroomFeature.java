package com.davenonymous.bonsaigen.mixins;

import com.davenonymous.bonsaigen.client.multiblock.MultiBlockFromFeatureGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHugeMushroomFeature.class)
public class MixinAbstractHugeMushroomFeature {

	@Inject(at = @At("HEAD"), method = "isValidPosition(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;ILnet/minecraft/core/BlockPos$MutableBlockPos;Lnet/minecraft/world/level/levelgen/feature/configurations/HugeMushroomFeatureConfiguration;)Z", cancellable = true)
	private void isValidPosition(LevelAccessor level, BlockPos pos, int maxHeight, BlockPos.MutableBlockPos mutablePos, HugeMushroomFeatureConfiguration config, CallbackInfoReturnable<Boolean> cir) {
		if(MultiBlockFromFeatureGenerator.GENERATING_MODELS) {
			cir.setReturnValue(true);
			cir.cancel();
		}
	}
}
