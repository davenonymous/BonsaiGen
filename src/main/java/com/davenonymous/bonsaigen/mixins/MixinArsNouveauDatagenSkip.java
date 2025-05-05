package com.davenonymous.bonsaigen.mixins;

import com.davenonymous.bonsaigen.BonsaiGen;
import com.hollingsworth.arsnouveau.common.datagen.ModDatagen;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModDatagen.class)
public class MixinArsNouveauDatagenSkip {

	@Inject(at = @At("HEAD"), method = "datagen(Lnet/neoforged/neoforge/data/event/GatherDataEvent;)V", cancellable = true)
	private static void datagen(GatherDataEvent event, CallbackInfo cir) {
		BonsaiGen.LOGGER.info("Skipping Ars Nouveau Datagen");
		cir.cancel();
	}
}
