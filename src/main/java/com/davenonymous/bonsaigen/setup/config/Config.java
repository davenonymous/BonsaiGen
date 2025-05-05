package com.davenonymous.bonsaigen.setup.config;

import com.davenonymous.bonsaigen.BonsaiGen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = BonsaiGen.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
	public static final ModConfigSpec COMMON_SPEC;
	public static final PackGenConfig PackGen;

	static {
		ModConfigSpec.Builder commonBuilder = new ModConfigSpec.Builder();
		PackGen = new PackGenConfig(commonBuilder);
		COMMON_SPEC = commonBuilder.build();

	}

	@SubscribeEvent
	static void onLoad(final ModConfigEvent event) {
		if(event.getConfig().getSpec() == COMMON_SPEC) {
			PackGen.load();
		}
	}
}
