package com.davenonymous.bonsaigen.setup.data;

public enum PoolType {
	LOG("log"),
	LEAVES("leaves"),
	OTHER("other");

	public final String name;

	PoolType(String name) {
		this.name = name;
	}
}
