package com.davenonymous.bonsaigen.gametest;

import com.davenonymous.bonsaigen.BonsaiGen;
import com.davenonymous.bonsaigen.command.generate.GenerateDataPack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BonsaiGen.MODID)
public class GenerationTests {

	@PrefixGameTestTemplate(false)
	@GameTest(template = "empty")
	public static void generateDatapacks(GameTestHelper helper) {
		//ChunkPos chunkPos = new ChunkPos(helper.testInfo.getStructureBlockPos());
		ChunkPos chunkPos = ChunkPos.ZERO;
		helper.makeMockPlayer(GameType.SPECTATOR).moveTo(chunkPos.getMiddleBlockX(), 40, chunkPos.getMiddleBlockZ());
		boolean success = GenerateDataPack.generateDataPack(
			"--all", helper.getLevel(), chunkPos, component -> {
				BonsaiGen.LOGGER.info(component.getString());
			}
		);

		if(success) {
			helper.succeed();
		} else {
			helper.fail("Data pack generation failed");
		}
	}
}
