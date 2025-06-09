package com.davenonymous.bonsaigen;

import com.davenonymous.bonsaigen.setup.ModCommands;
import com.davenonymous.bonsaigen.setup.config.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(BonsaiGen.MODID)
public class BonsaiGen {
	public static final String MODID = "bonsaigen";
	public static final String BASE_MODID = "bonsaitrees4";
	public static final Logger LOGGER = LogUtils.getLogger();
	public static ModContainer CONTAINER;

	public BonsaiGen(IEventBus modEventBus, ModContainer modContainer) {
		CONTAINER = modContainer;

		modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);

		ModCommands.ARGUMENT_TYPES.register(modEventBus);
	}

	public static ResourceLocation resource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	public static ResourceLocation bonsaiResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(BASE_MODID, path);
	}

}
