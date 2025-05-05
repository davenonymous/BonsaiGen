package com.davenonymous.bonsaigen.datagen;

import com.davenonymous.bonsaigen.BonsaiGen;
import com.davenonymous.bonsaigen.client.multiblock.MultiBlockFromFeatureGenerator;
import com.davenonymous.bonsaigen.lib.Helpers;
import com.davenonymous.bonsaigen.setup.cache.FixedTreeGenerationCache;
import com.davenonymous.bonsaigen.setup.data.ModelGenerationInfo;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DGTreeModelProvider implements DataProvider {
	private final ExistingFileHelper exFileHelper;
	private final PackOutput output;
	public Map<ResourceLocation, MultiBlockFromFeatureGenerator.Result> models;
	public Map<ResourceLocation, ResourceLocation> itemToModel;
	private final ServerLevel level;
	private final RegistryAccess registryAccess;
	private final ChunkPos chunkPos;
	private final MultiBlockFromFeatureGenerator featureToModelGen;

	public DGTreeModelProvider(PackOutput output, ExistingFileHelper exFileHelper, ServerLevel level, RegistryAccess registryAccess, ChunkPos chunkPos) {
		this.output = output;
		this.exFileHelper = exFileHelper;
		this.models = new HashMap<>();
		this.itemToModel = new HashMap<>();
		this.level = level;
		this.registryAccess = registryAccess;
		this.chunkPos = chunkPos;
		this.featureToModelGen = new MultiBlockFromFeatureGenerator(level);
		this.featureToModelGen.setChunk(chunkPos);
	}

	public DGTreeModelProvider addModel(ResourceLocation itemId, ResourceLocation featureId, MultiBlockFromFeatureGenerator.Result model) {
		this.models.put(featureId, model);
		this.itemToModel.put(itemId, featureId);
		return this;
	}

	public int count() {
		return this.models.size();
	}

	public ModelGenerationInfo getModelGenerationInfo(ItemLike stack) {
		return FixedTreeGenerationCache.FIXED_TREE_GENERATION.containsKey(stack.asItem())
			   ? FixedTreeGenerationCache.FIXED_TREE_GENERATION.get(stack.asItem())
			   : ModelGenerationInfo.EMPTY();
	}

	public <FC extends FeatureConfiguration, F extends Feature<FC>> DGTreeModelProvider addFeature(ItemLike item, F feature, FC featureConfiguration,
		ModelGenerationInfo modelGenInfo, Consumer<Component> onMessage)
	{

		MultiBlockFromFeatureGenerator.Result modelGenResult = featureToModelGen.fromFeature(feature, featureConfiguration, modelGenInfo);
		modelGenResult.messages().forEach(onMessage);
		if(modelGenResult == null || modelGenResult.geometry() == null) {
			onMessage.accept(Component.literal("Failed to generate model for " + item.asItem().getDescriptionId()));
			return this;
		}
		ResourceLocation itemId = item.asItem().builtInRegistryHolder().getKey().location();
		this.addModel(itemId, modelGenResult.featureId(), modelGenResult);

		return this;
	}

	public <FC extends FeatureConfiguration, F extends Feature<FC>> DGTreeModelProvider addFeature(ItemLike item, F feature, FC featureConfiguration,
		Consumer<Component> onMessage)
	{

		ModelGenerationInfo modelGenInfo = getModelGenerationInfo(item);
		MultiBlockFromFeatureGenerator.Result modelGenResult = featureToModelGen.fromFeature(feature, featureConfiguration, modelGenInfo);
		modelGenResult.messages().forEach(onMessage);
		if(modelGenResult == null || modelGenResult.geometry() == null) {
			onMessage.accept(Component.literal("Failed to generate model for " + item.asItem().getDescriptionId()));
			return this;
		}
		ResourceLocation itemId = item.asItem().builtInRegistryHolder().getKey().location();
		this.addModel(itemId, modelGenResult.featureId(), modelGenResult);

		return this;
	}

	public DGTreeModelProvider addFeature(ItemLike item, ResourceKey<ConfiguredFeature<?, ?>> featureId, Consumer<Component> onMessage) {
		ModelGenerationInfo modelGenInfo = getModelGenerationInfo(item);
		MultiBlockFromFeatureGenerator.Result modelGenResult = featureToModelGen.fromFeature(featureId, modelGenInfo);
		modelGenResult.messages().forEach(onMessage);
		if(modelGenResult == null || modelGenResult.geometry() == null) {
			onMessage.accept(Component.literal("Failed to generate model for " + item.asItem().getDescriptionId()));
			return this;
		}
		ResourceLocation itemId = item.asItem().builtInRegistryHolder().getKey().location();
		this.addModel(itemId, featureId.location(), modelGenResult);

		return this;
	}

	@SuppressWarnings("ConstantConditions")
	public DGTreeModelProvider gatherTrees(String modId, Consumer<Component> onMessage) {
		Helpers.forAllSaplingBlocks((treeGrower, saplingItem) -> {
			ItemStack saplingStack = new ItemStack(saplingItem);
			String modSource = saplingItem.getCreatorModId(saplingStack);
			if(modSource == null || !modSource.equals(modId)) {
				return;
			}

			ModelGenerationInfo modelGenInfo = getModelGenerationInfo(saplingItem);
			ResourceKey<ConfiguredFeature<?, ?>> featureId = Helpers.getTreeFeature(treeGrower, modelGenInfo);
			this.addFeature(saplingItem, featureId, onMessage);
		});

		return this;
	}

	public DGTreeModelProvider gatherFunghi(String modId, Consumer<Component> onMessage) {
		Helpers.forAllFungusBlocks((featureId, item) -> {
			ItemStack stack = new ItemStack(item);
			String modSource = item.getCreatorModId(stack);
			if(modSource == null || !modSource.equals(modId)) {
				return;
			}

			this.addFeature(item, featureId, onMessage);
		});

		Helpers.forAllMushroomBlocks((featureId, item) -> {
			ItemStack stack = new ItemStack(item);
			String modSource = item.getCreatorModId(stack);
			if(modSource == null || !modSource.equals(modId)) {
				return;
			}

			this.addFeature(item, featureId, onMessage);
		});

		return this;
	}


	@Override
	public CompletableFuture<?> run(CachedOutput cachedOutput) {
		CompletableFuture<?>[] futures = new CompletableFuture[this.models.size()];
		int i = 0;

		for(var modelEntry : this.models.entrySet()) {
			var model = modelEntry.getValue();
			var item = modelEntry.getKey();
			futures[i++] = saveStable(cachedOutput, model.geometry().serializePretty(), this.getPath(item));
		}

		return CompletableFuture.allOf(futures);
	}

	static CompletableFuture<?> saveStable(CachedOutput output, String json, Path path) {
		return CompletableFuture.runAsync(() -> {
			try {
				ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
				HashingOutputStream hashingoutputstream = new HashingOutputStream(Hashing.sha1(), bytearrayoutputstream);
				OutputStreamWriter writer = new OutputStreamWriter(hashingoutputstream, StandardCharsets.UTF_8);
				writer.write(json);
				writer.close();

				output.writeIfNeeded(path, bytearrayoutputstream.toByteArray(), hashingoutputstream.hash());
			} catch (IOException ioexception) {
				LOGGER.error("Failed to save file to {}", path, ioexception);
			}
		}, Util.backgroundExecutor());
	}

	protected Path getPath(ResourceLocation loc) {
		return this.output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
			.resolve(BonsaiGen.BASE_MODID)
			.resolve("models")
			.resolve("multiblock")
			.resolve(loc.getNamespace())
			.resolve(loc.getPath() + ".json");
	}

	@Override
	public String getName() {
		return "Tree Model Provider";
	}
}
