package com.davenonymous.bonsaigen.mixins;

import alexthw.ars_elemental.datagen.Datagen;
import com.davenonymous.bonsaigen.BonsaiGen;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Datagen.class)
public class MixinArsElementalDatagenSkip {

	@Inject(at = @At("HEAD"), method = "gatherData(Lnet/neoforged/neoforge/data/event/GatherDataEvent;)V", cancellable = true)
	private static void gatherData(GatherDataEvent event, CallbackInfo cir) {
		BonsaiGen.LOGGER.info("Skipping Ars Elemental Datagen");
		cir.cancel();
	}
}
