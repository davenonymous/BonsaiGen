package com.davenonymous.bonsaitrees.command.generate;

import com.davenonymous.bonsaitrees.multiblock.MultiBlockGeometryBase;
import com.davenonymous.bonsaitrees.networking.GeometryToClipboard;
import com.davenonymous.bonsaitrees.setup.config.PackGenConfig;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

public class GenerateModel implements Command<CommandSourceStack> {
	private static final GenerateModel INSTANCE = new GenerateModel();

	private GenerateModel() {
	}

	public static ArgumentBuilder<CommandSourceStack, ?> register(CommandDispatcher<CommandSourceStack> dispatcher) {
		return Commands.literal("model").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(INSTANCE));
	}


	@Override
	public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerLevel world = context.getSource().getLevel();
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
		MultiBlockGeometryBase geometry = MultiBlockGeometryBase.floodfill(world, pos);

		String result = String.format(
			"Flood fill complete. Size=%dx%dx%d, Blocks=%d, States=%d",
			geometry.getSize().getX() + 1,
			geometry.getSize().getY() + 1,
			geometry.getSize().getZ() + 1,
			geometry.getBlockCount(),
			geometry.getStateCount()
		);
		context.getSource().sendSuccess(() -> Component.literal(result), false);

		if(geometry.getBlockCount() > PackGenConfig.maximumModelBlocks) {
			context.getSource().sendFailure(Component.translatable("commands.bonsaitrees4.generate.model.flood_fill.too_large", PackGenConfig.maximumModelBlocks));
			return 0;
		}

		if(geometry.getStateCount() > PackGenConfig.maximumModelBlockStates) {
			context.getSource().sendFailure(Component.translatable("commands.bonsaitrees4.generate.model.flood_fill.too_many_states", PackGenConfig.maximumModelBlockStates));
			return 0;
		}

		context.getSource().sendSuccess(() -> Component.translatable("commands.bonsaitrees4.generate.model.copied_to_clipboard"), false);
		PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(), new GeometryToClipboard(geometry.serializePretty()));

		return 0;
	}

}
