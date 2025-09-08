package com.davenonymous.bonsaigen.setup;


import com.davenonymous.bonsaigen.BonsaiGen;
import com.davenonymous.bonsaigen.command.BonsaiCommand;
import com.davenonymous.bonsaigen.command.arguments.ModOrAllArgument;
import com.davenonymous.bonsaitrees.BonsaiTrees;
import com.davenonymous.bonsaitrees.command.arguments.SaplingArgument;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = BonsaiGen.MODID)
public class ModCommands {
	public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES = DeferredRegister.create(BuiltInRegistries.COMMAND_ARGUMENT_TYPE, BonsaiGen.MODID);

	private static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<ModOrAllArgument>> MODORALL_ARGUMENT_TYPE = ARGUMENT_TYPES.register(
		"mod_or_all", () -> ArgumentTypeInfos.registerByClass(
			ModOrAllArgument.class,
			SingletonArgumentInfo.contextFree(ModOrAllArgument::modOrAllArgument)
		)
	);

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
