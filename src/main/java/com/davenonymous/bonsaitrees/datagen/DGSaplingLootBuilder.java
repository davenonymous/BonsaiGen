package com.davenonymous.bonsaitrees.datagen;

import com.davenonymous.bonsaitrees.BonsaiTrees;
import com.davenonymous.bonsaitrees.client.multiblock.MultiBlockFromFeatureGenerator;
import com.davenonymous.bonsaitrees.setup.data.PoolType;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;

public class DGSaplingLootBuilder implements LootTableSubProvider {

	public HolderLookup.Provider lookupProvider;
	private final Map<ResourceKey<LootTable>, LootTable.Builder> tables = new HashMap<>();
	public String forMod;
	public Map<ResourceLocation, MultiBlockFromFeatureGenerator.Result> treeBlocks;

	private DGSaplingLootBuilder(HolderLookup.Provider lookupProvider, String forMod, Map<ResourceLocation, MultiBlockFromFeatureGenerator.Result> treeBlocks) {
		this.lookupProvider = lookupProvider;
		this.forMod = forMod;
		this.treeBlocks = treeBlocks;
	}

	public static LootTableProvider.SubProviderEntry forMod(String mod, Map<ResourceLocation, MultiBlockFromFeatureGenerator.Result> treeBlocks) {
		return new LootTableProvider.SubProviderEntry(provider -> new DGSaplingLootBuilder(provider, mod, treeBlocks), LootContextParamSets.ALL_PARAMS);
	}

	private LootPool.Builder pool(PoolType type, float rolls, float chance, @NotNull ItemLike... item) {
		var pool = LootPool.lootPool()
			.setRolls(ConstantValue.exactly(rolls))
			.setBonusRolls(ConstantValue.exactly(rolls / 2))
			.name(type.name);
		if(chance < 1f) {
			pool.when(LootItemRandomChanceCondition.randomChance(chance));
		}

		Arrays.stream(item).map(LootItem::lootTableItem).forEach(pool::add);
		return pool;
	}

	protected LootPool.Builder logPool(@NotNull ItemLike... item) {
		return pool(PoolType.LOG, 3, 0.5f, item);
	}

	protected LootPool.Builder leavesPool(@NotNull ItemLike... item) {
		return pool(PoolType.LEAVES, 2, 0.5f, item);
	}

	protected LootPool.Builder otherPool(float chance, @NotNull ItemLike... item) {
		return pool(PoolType.OTHER, 1, chance, item);
	}


	private static class UnvalidatedNestedLootTable extends NestedLootTable {
		public UnvalidatedNestedLootTable(Either<ResourceKey<LootTable>, LootTable> contents, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
			super(contents, weight, quality, conditions, functions);
		}

		public static LootPoolSingletonContainer.Builder<?> lootTableReference(ResourceKey<LootTable> lootTable) {
			return simpleBuilder((p_331271_, p_331120_, p_331361_, p_331392_) -> {
				return new UnvalidatedNestedLootTable(Either.left(lootTable), p_331271_, p_331120_, p_331361_, p_331392_);
			});
		}

		@Override
		public void validate(ValidationContext context) {
			// Do nothing
		}
	}

	@Override
	public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> consumer) {
		this.treeBlocks.forEach((saplingLocation, result) -> {
			String newResourcePath = "bonsai/" + saplingLocation.getNamespace() + "/" + saplingLocation.getPath();
			ResourceKey<LootTable> saplingLootTable = ResourceKey.create(Registries.LOOT_TABLE, BonsaiTrees.resource(newResourcePath));
			LootTable.Builder table = LootTable.lootTable();

			Set<ResourceKey<LootTable>> alreadyAdded = new HashSet<>();
			boolean hasLogs = false;
			boolean hasLeaves = false;
			boolean hasOther = false;
			var logPool = logPool();
			var leavesPool = leavesPool();
			var otherPool = otherPool(0.2f);

			for(var state : result.geometry().ref().values()) {
				ResourceKey<LootTable> innerTableKey = state.getBlock().getLootTable();
				if(alreadyAdded.contains(innerTableKey)) {
					continue;
				}

				if(state.is(BlockTags.BEEHIVES)) {
					return;
				}

				if(state.is(BlockTags.LOGS)) {
					logPool.add(UnvalidatedNestedLootTable.lootTableReference(innerTableKey).setWeight(1));
					hasLogs = true;
				} else if(state.is(BlockTags.LEAVES)) {
					leavesPool.add(UnvalidatedNestedLootTable.lootTableReference(innerTableKey).setWeight(1));
					hasLeaves = true;
				} else {
					otherPool.add(UnvalidatedNestedLootTable.lootTableReference(innerTableKey).setWeight(1));
					hasOther = true;
				}
				alreadyAdded.add(innerTableKey);
			}

			if(hasLogs) {
				table.withPool(logPool);
			}
			if(hasLeaves) {
				table.withPool(leavesPool);
			}
			if(hasOther) {
				table.withPool(otherPool);
			}

			tables.put(saplingLootTable, table);
		});

		for(Map.Entry<ResourceKey<LootTable>, LootTable.Builder> entry : tables.entrySet()) {
			consumer.accept(entry.getKey(), entry.getValue());
		}
	}
}
