package com.davenonymous.bonsaigen.datagen;


import com.davenonymous.bonsaigen.BonsaiGen;
import com.davenonymous.bonsaigen.setup.data.SoilType;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DGDataRegistries {
	public static final ResourceKey<Registry<SoilType>> SOILTYPE_REGISTRY_KEY = ResourceKey.createRegistryKey(
		BonsaiGen.bonsaiResource("soiltype"));
	private static ResourceKey<SoilType> builtIn(String name) {
		return ResourceKey.create(SOILTYPE_REGISTRY_KEY, BonsaiGen.bonsaiResource(name));
	}

	private static void register(BootstrapContext<SoilType> context, ResourceKey<SoilType> key, ItemStack defaultItem) {
		String translationKey = BonsaiGen.BASE_MODID + ".tooltip.soil." + key.location().getNamespace() + "." + key.location().getPath();
		context.register(key, new SoilType(key.location(), defaultItem, translationKey));
	}

	public static final ResourceKey<SoilType> DIRT = builtIn("dirt");
	public static final ResourceKey<SoilType> SAND = builtIn("sand");
	public static final ResourceKey<SoilType> WATER = builtIn("water");
	public static final ResourceKey<SoilType> LAVA = builtIn("lava");
	public static final ResourceKey<SoilType> STONE = builtIn("stone");
	public static final ResourceKey<SoilType> END_STONE = builtIn("end_stone");
	public static final ResourceKey<SoilType> NETHER_STONE = builtIn("nether_stone");
	public static final ResourceKey<SoilType> MYCELIUM = builtIn("mycelium");
	public static final ResourceKey<SoilType> NYLIUM = builtIn("nylium");


	public static RegistrySetBuilder create() {
		return new RegistrySetBuilder().add(
			SOILTYPE_REGISTRY_KEY, bootstrap -> {
				register(bootstrap, DIRT, new ItemStack(Items.GRASS_BLOCK));
				register(bootstrap, SAND, new ItemStack(Items.SAND));
				register(bootstrap, WATER, new ItemStack(Items.WATER_BUCKET));
				register(bootstrap, LAVA, new ItemStack(Items.LAVA_BUCKET));
				register(bootstrap, STONE, new ItemStack(Items.STONE));
				register(bootstrap, END_STONE, new ItemStack(Items.END_STONE));
				register(bootstrap, NETHER_STONE, new ItemStack(Items.NETHERRACK));
				register(bootstrap, MYCELIUM, new ItemStack(Items.MYCELIUM));
				register(bootstrap, NYLIUM, new ItemStack(Items.CRIMSON_NYLIUM));
			}
		);
	}
}
