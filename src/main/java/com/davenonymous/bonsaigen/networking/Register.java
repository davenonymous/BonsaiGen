package com.davenonymous.bonsaigen.networking;

import com.davenonymous.bonsaigen.BonsaiGen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = BonsaiGen.MODID)
public class Register {
	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("1");

		registrar.playToClient(
			GeometryToClipboard.TYPE,
			GeometryToClipboard.STREAM_CODEC,
			GeometryToClipboard::handle
		);
	}
}
