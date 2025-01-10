package com.davenonymous.bonsaitrees.networking;

import com.davenonymous.bonsaitrees.BonsaiTrees;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GeometryToClipboard(String geometry) implements CustomPacketPayload {
	public static final Type<GeometryToClipboard> TYPE = new Type<>(BonsaiTrees.resource("multiblock_geometry"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GeometryToClipboard> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, GeometryToClipboard::geometry,
		GeometryToClipboard::new
	);

	public static void handle(GeometryToClipboard message, IPayloadContext context) {
		Minecraft.getInstance().keyboardHandler.setClipboard(message.geometry());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
