package com.davenonymous.bonsaigen.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModOrAllArgument implements ArgumentType<String> {

	private static final List<String> EXAMPLES = Arrays.asList("*", "bonsaitrees", "inventorysorter");

	public static ModOrAllArgument modOrAllArgument() {
		return new ModOrAllArgument();
	}

	@Override
	public String parse(final StringReader reader) throws CommandSyntaxException {
		return reader.readUnquotedString();
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
		List<String> modList = new LinkedList<>(ModList.get().applyForEachModContainer(ModContainer::getModId).toList());
		modList.addFirst("--all");
		return SharedSuggestionProvider.suggest(modList.stream(), builder);
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}

}
