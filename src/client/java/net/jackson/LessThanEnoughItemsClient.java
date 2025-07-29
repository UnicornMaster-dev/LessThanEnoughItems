package net.jackson;

import net.fabricmc.api.ClientModInitializer;

public class LessThanEnoughItemsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ItemListOverlay.reloadItems();
		System.out.println("ItemListMod Initialized");
	}
}