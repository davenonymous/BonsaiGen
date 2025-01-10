package com.davenonymous.bonsaitrees.lib.util;

import java.util.function.Supplier;

public class CacheStorage<T> {
	private T value;
	private Supplier<T> loader;

	public CacheStorage(Supplier<T> loader) {
		this.loader = loader;
	}

	public CacheStorage(T value) {
		this.value = value;
	}

	public T get() {
		if(value == null) {
			value = loader.get();
		}
		return value;
	}

	public void update(T value) {
		this.value = value;
	}

	public void invalidate() {
		value = null;
	}
}