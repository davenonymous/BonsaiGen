package com.davenonymous.bonsaitrees.lib.util;

import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class TagUtils {

	public static <E> Set<E> getTags(Registry<E> registry, TagKey<E> tag) {
		return Collections.unmodifiableSet(registry.getOrCreateTag(tag).stream()
			.map(eHolder -> eHolder.value())
			.collect(Collectors.toSet()));
	}
}