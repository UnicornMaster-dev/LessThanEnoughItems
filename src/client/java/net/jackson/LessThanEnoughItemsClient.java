package net.jackson;

import net.fabricmc.api.ClientModInitializer;

public class LessThanEnoughItemsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Load config first
		RecipeViewerConfig.getInstance();

		// Initialize items with config applied
		ItemListOverlay.reloadItems();

		System.out.println("LessThanEnoughItems Initialized - Config loaded and items filtered");
	}
}