package com.davenonymous.bonsaigen.setup.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;

public class PackGenConfig {

	public final ModConfigSpec.BooleanValue REMOVE_CACHE;
	public final ModConfigSpec.BooleanValue CREATE_ZIP;
	public final ModConfigSpec.BooleanValue TRIM_TRUNKS;
	public final ModConfigSpec.IntValue TRIM_TRUNK_THRESHOLD;
	public final ModConfigSpec.ConfigValue<String> PATH;
	public final ModConfigSpec.ConfigValue<List<? extends String>> MOD_BLACKLIST;
	public final ModConfigSpec.IntValue MAXIMUM_MODEL_BLOCKS;
	public final ModConfigSpec.IntValue MAXIMUM_MODEL_BLOCKSTATES;

	public static boolean removeCache;
	public static boolean createZip;
	public static boolean trimTrunks;
	public static int trimTrunkThreshold;
	public static String path;
	public static HashSet<String> modBlacklist;
	public static int maximumModelBlocks;
	public static int maximumModelBlockStates;

	public PackGenConfig(ModConfigSpec.Builder builder) {
		builder.push("packgen");
		REMOVE_CACHE = builder
			.comment("Remove the cache after generating packs")
			.translation("bonsaigen.configuration.packgen.remove_cache")
			.define("noCache", true);

		CREATE_ZIP = builder
			.comment("Create a zip file of the generated packs")
			.translation("bonsaigen.configuration.packgen.create_zip")
			.define("createZip", false);

		TRIM_TRUNKS = builder
			.comment("Trim trunks of trees to reduce height")
			.translation("bonsaigen.configuration.packgen.trim_trunks")
			.define("trimTrunks", true);

		TRIM_TRUNK_THRESHOLD = builder
			.comment("The height at which to trim trunks")
			.translation("bonsaigen.configuration.packgen.trim_trunk_threshold")
			.defineInRange("trimTrunkThreshold", 5, 1, 0xff);

		PATH = builder
			.comment("The path to save the generated packs")
			.translation("bonsaigen.configuration.packgen.path")
			.define("path", "bonsai-generated");

		MOD_BLACKLIST = builder
			.comment("Mods to exclude from pack generation")
			.translation("bonsaigen.configuration.packgen.mod_blacklist")
			.<String>defineListAllowEmpty(
				"modBlacklist", List.of(), String::new, p -> p instanceof String s);

		MAXIMUM_MODEL_BLOCKS = builder
			.comment("Maximum number of blocks during flood fill")
			.translation("bonsaigen.configuration.packgen.maximum_model_blocks")
			.defineInRange("maximumModelBlocks", 512, 1, 65536);

		MAXIMUM_MODEL_BLOCKSTATES = builder
			.comment("Maximum number of block states during flood fill")
			.translation("bonsaigen.configuration.packgen.maximum_model_blockstates")
			.defineInRange("maximumModelBlockStates", 52, 1, 52);

		builder.pop();
	}

	public void load() {
		removeCache = REMOVE_CACHE.get();
		createZip = CREATE_ZIP.get();
		trimTrunks = TRIM_TRUNKS.get();
		trimTrunkThreshold = TRIM_TRUNK_THRESHOLD.get();
		path = PATH.get();
		modBlacklist = new HashSet<>(MOD_BLACKLIST.get());
		maximumModelBlocks = MAXIMUM_MODEL_BLOCKS.get();
		maximumModelBlockStates = MAXIMUM_MODEL_BLOCKSTATES.get();
	}
}
