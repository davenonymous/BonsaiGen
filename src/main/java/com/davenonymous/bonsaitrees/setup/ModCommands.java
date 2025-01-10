package com.davenonymous.bonsaitrees.setup;


import com.davenonymous.bonsaitrees.BonsaiTrees;
import com.davenonymous.bonsaitrees.command.BonsaiCommand;
import com.davenonymous.bonsaitrees.command.arguments.ModOrAllArgument;
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

@EventBusSubscriber(modid = BonsaiTrees.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModCommands {
	public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES = DeferredRegister.create(BuiltInRegistries.COMMAND_ARGUMENT_TYPE, BonsaiTrees.MODID);

	private static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<SaplingArgument>> SAPLING_ARGUMENT_TYPE = ARGUMENT_TYPES.register(
		"sapling", () -> ArgumentTypeInfos.registerByClass(
			SaplingArgument.class,
			SingletonArgumentInfo.contextFree(SaplingArgument::saplingArgument)
		)
	);

	private static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<ModOrAllArgument>> MODORALL_ARGUMENT_TYPE = ARGUMENT_TYPES.register(
		"mod_or_all", () -> ArgumentTypeInfos.registerByClass(
			ModOrAllArgument.class,
			SingletonArgumentInfo.contextFree(ModOrAllArgument::modOrAllArgument)
		)
	);

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		ModCommands.register(event.getDispatcher(), event.getBuildContext());
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext pContext) {
		dispatcher.register(
			BonsaiCommand.register(dispatcher)
		);
	}
}
