package com.davenonymous.bonsaigen.lib;

import com.davenonymous.bonsaigen.BonsaiGen;
import com.google.common.hash.HashCode;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.locating.IModFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class FileCopyGenerator implements DataProvider {
	private final PackOutput output;
	private final Map<Path, Path> files;

	public FileCopyGenerator(PackOutput output) {
		this.output = output;
		this.files = new HashMap<>();
	}

	public FileCopyGenerator add(Path source, Path target) {
		this.files.put(source, target);
		return this;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		return CompletableFuture.runAsync(() -> {
			IModFile bonsaiMod = ModList.get().getModFileById(BonsaiGen.MODID).getFile();

			for(Map.Entry<Path, Path> entry : this.files.entrySet()) {
				Path source = bonsaiMod.findResource(entry.getKey().toString());
				Path target = this.output.getOutputFolder().resolve(entry.getValue());
				try {
					var supplier = IoSupplier.create(source).get();
					byte[] pngBytes = supplier.readAllBytes();
					output.writeIfNeeded(target, pngBytes, HashCode.fromBytes(pngBytes));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	@Override
	public final String getName() {
		return "File Copy Generator";
	}
}
