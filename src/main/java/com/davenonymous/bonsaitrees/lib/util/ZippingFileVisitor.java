package com.davenonymous.bonsaitrees.lib.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZippingFileVisitor extends SimpleFileVisitor<Path> {
	private final Path basePath;
	private final ZipOutputStream zipOutput;

	public ZippingFileVisitor(Path basePath, ZipOutputStream zipOutput) {
		this.basePath = basePath;
		this.zipOutput = zipOutput;
	}

	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		if(file.toString().contains(".cache")) {
			return FileVisitResult.SKIP_SUBTREE;
		}
		var relative = file.subpath(basePath.getNameCount(), file.getNameCount());
		createZipEntryFromFile(file, relative.toString(), zipOutput);
		return FileVisitResult.CONTINUE;
	}

	public static void createZipEntryFromFile(Path file, String zipFileName, ZipOutputStream zipOutput) throws IOException {
		var zipEntry = new ZipEntry(zipFileName);
		zipOutput.putNextEntry(zipEntry);

		var in = new FileInputStream(file.toFile());
		byte[] buffer = new byte[1024];
		int len;
		while((len = in.read(buffer)) > 0) {
			zipOutput.write(buffer, 0, len);
		}
		in.close();
		zipOutput.closeEntry();
	}
}