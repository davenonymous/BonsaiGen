package com.davenonymous.bonsaigen.command;

import com.davenonymous.bonsaigen.command.generate.GenerateDataPack;
import com.davenonymous.bonsaigen.command.generate.GenerateModel;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class BonsaiCommand {

	public static boolean canDestroyWorld(CommandSourceStack source) {
		if(!source.hasPermission(4)) {
			return false;
		}
		if(source.getServer().isDedicatedServer()) {
			return false;
		}

		if(!source.getLevel().isFlat()) {
			return false;
		}

		return true;
	}

	public static LiteralArgumentBuilder<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
		return Commands.literal("bonsai")
			.then(Commands.literal("generate")
				.then(GenerateModel.register(dispatcher))
				.then(Commands.literal("data-pack")
					.requires(BonsaiCommand::canDestroyWorld)
					.then(GenerateDataPack.register(dispatcher))
				)
			);
	}
}
