package com.davenonymous.bonsaigen.setup;


import com.davenonymous.bonsaigen.BonsaiGen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforgespi.locating.IModFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@EventBusSubscriber(modid = BonsaiGen.MODID)
public class ModDataPacks {
	private static final PackSelectionConfig DATAPACK_SELECTION_CONFIG = new PackSelectionConfig(true, Pack.Position.BOTTOM, false);

	@SubscribeEvent
	public static void addPackFinder(AddPackFindersEvent event) {
		if(event.getPackType() == PackType.SERVER_DATA) {
			IModFile bonsaiModFile = ModList.get().getModFileById(BonsaiGen.MODID).getFile();

			for(var modInfo : ModList.get().getMods()) {
				String modId = modInfo.getModId();

				try {
					Path resourcePath = bonsaiModFile.findResource("generation_config", modId);
					PackLocationInfo packLocationInfo = new PackLocationInfo(
						modId + "_generation_config",
						Component.literal("BonsaiGen generation info for: " + modInfo.getDisplayName()),
						PackSource.BUILT_IN,
						Optional.empty()
					);
					PathPackResources packResources = new PathPackResources(packLocationInfo, resourcePath);
					PackMetadataSection packMeta = packResources.getMetadataSection(MetadataSectionType.fromCodec("pack", PackMetadataSection.CODEC));
					if(packMeta == null) {
						continue;
					}

					PathPackResources.PathResourcesSupplier pathResourcesSupplier = new PathPackResources.PathResourcesSupplier(resourcePath);
					Pack pack = Pack.readMetaAndCreate(packLocationInfo, pathResourcesSupplier, event.getPackType(), DATAPACK_SELECTION_CONFIG).hidden();
					event.addRepositorySource(consumer -> {
						consumer.accept(pack);
						BonsaiGen.LOGGER.info("Added BonsaiGen datapack for mod: " + modId);
					});
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

			}

		}
	}
}
