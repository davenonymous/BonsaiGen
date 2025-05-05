package com.davenonymous.bonsaigen.setup;


import com.davenonymous.bonsaigen.BonsaiGen;
import com.davenonymous.bonsaigen.command.BonsaiCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = BonsaiGen.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModCommands {

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(BonsaiCommand.register(event.getDispatcher()));
		ModCommands.register(event.getDispatcher(), event.getBuildContext());
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext pContext) {
		dispatcher.register(
			BonsaiCommand.register(dispatcher)
		);
	}
}
