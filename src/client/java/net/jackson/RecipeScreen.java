package net.jackson;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.util.*;

public class RecipeScreen extends Screen {
    private final Item targetItem;
    private JsonObject recipe;

    private int ingredientCycleIndex = 0;
    private long lastCycleTime = 0;
    private static final long CYCLE_INTERVAL_MS = 1000; // 1 second per ingredient

    public RecipeScreen(Item item) {
        super(Text.literal("Recipe: " + Registries.ITEM.getId(item).toString()));
        this.targetItem = item;
        loadRecipe();
    }

    private void loadRecipe() {
        String itemName = Registries.ITEM.getId(targetItem).getPath();
        Identifier file = Identifier.of("jackson", "recipes/" + itemName + ".json");

        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(
                        getClass().getClassLoader().getResourceAsStream("assets/" + file.getNamespace() + "/" + file.getPath()))
        )) {
            recipe = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            System.err.println("Failed to load recipe for " + itemName);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        if (recipe == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No recipe found").getString(), width / 2, height / 2, 0xFF0000);
            return;
        }

        String type = recipe.get("type").getAsString();

        if (type.equals("minecraft:crafting_shaped")) {
            renderShaped(context, recipe);
        } else if (type.equals("minecraft:crafting_shapeless")) {
            renderShapeless(context, recipe);
        } else if (type.equals("minecraft:blasting") || type.equals("minecraft:smelting")) {
            renderCooking(context, recipe, type);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, "Unknown recipe type", width / 2, height / 2, 0xFF0000);
        }
    }

    private void renderCooking(DrawContext context, JsonObject json, String type) {
        // Handles both blasting and smelting
        int startX = width / 2 - 32;
        int startY = 60;
        int resultX = width / 2 + 50;
        int resultY = 80;

        // Handle single or multiple ingredients
        List<String> ingredientIds = new ArrayList<>();
        if (json.get("ingredient").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("ingredient");
            for (JsonElement el : arr) ingredientIds.add(el.getAsString());
        } else if (json.get("ingredient").isJsonPrimitive()) {
            ingredientIds.add(json.get("ingredient").getAsString());
        }

        // Cycle through ingredients if multiple
        if (ingredientIds.size() > 1) {
            long now = System.currentTimeMillis();
            if (now - lastCycleTime > CYCLE_INTERVAL_MS) {
                ingredientCycleIndex = (ingredientCycleIndex + 1) % ingredientIds.size();
                lastCycleTime = now;
            }
        } else {
            ingredientCycleIndex = 0;
        }

        // Draw ingredient
        if (!ingredientIds.isEmpty()) {
            String id = ingredientIds.get(ingredientCycleIndex);
            Item item = Registries.ITEM.get(Identifier.of(id));
            context.drawItem(new ItemStack(item), startX, startY);
        }

        // Draw arrow (furnace/blast icon could be added here)
        context.drawCenteredTextWithShadow(this.textRenderer, type.equals("minecraft:blasting") ? "-> (Blast) ->" : "-> (Smelt) ->", width / 2, startY + 8, 0xAAAAAA);

        // Draw result
        JsonObject result = json.getAsJsonObject("result");
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        ItemStack resultStack = new ItemStack(Registries.ITEM.get(Identifier.of(result.get("id").getAsString())), count);
        context.drawItem(resultStack, resultX, resultY);
    }

    private void renderShaped(DrawContext context, JsonObject json) {
        JsonArray pattern = json.getAsJsonArray("pattern");
        JsonObject key = json.getAsJsonObject("key");
        JsonObject result = json.getAsJsonObject("result");

        Map<Character, ItemStack> keyMap = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            char symbol = entry.getKey().charAt(0);
            String id = entry.getValue().getAsString();
            Item item = Registries.ITEM.get(Identifier.of(id));
            keyMap.put(symbol, new ItemStack(item));
        }

        int startX = width / 2 - 32;
        int startY = 60;

        for (int row = 0; row < pattern.size(); row++) {
            String line = pattern.get(row).getAsString();
            for (int col = 0; col < line.length(); col++) {
                char symbol = line.charAt(col);
                ItemStack stack = keyMap.getOrDefault(symbol, ItemStack.EMPTY);
                context.drawItem(stack, startX + col * 18, startY + row * 18);
            }
        }

        // Result
        int resultX = width / 2 + 50;
        int resultY = 80;
        ItemStack resultStack = new ItemStack(Registries.ITEM.get(Identifier.of(result.get("id").getAsString())), result.get("count").getAsInt());
        context.drawItem(resultStack, resultX, resultY);
    }

    private void renderShapeless(DrawContext context, JsonObject json) {
        // Support both "ingredients" and "ingredient" (array or string)
        List<String> ingredientIds = new ArrayList<>();
        if (json.has("ingredients")) {
            JsonArray ingredients = json.getAsJsonArray("ingredients");
            for (JsonElement el : ingredients) ingredientIds.add(el.getAsString());
        } else if (json.has("ingredient")) {
            JsonElement ing = json.get("ingredient");
            if (ing.isJsonArray()) {
                for (JsonElement el : ing.getAsJsonArray()) ingredientIds.add(el.getAsString());
            } else if (ing.isJsonPrimitive()) {
                ingredientIds.add(ing.getAsString());
            }
        }
        JsonObject result = json.getAsJsonObject("result");
        int startX = width / 2 - 32;
        int startY = 60;
        for (int i = 0; i < ingredientIds.size(); i++) {
            String id = ingredientIds.get(i);
            Item item = Registries.ITEM.get(Identifier.of(id));
            context.drawItem(new ItemStack(item), startX + (i % 3) * 18, startY + (i / 3) * 18);
        }
        int resultX = width / 2 + 50;
        int resultY = 80;
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        ItemStack resultStack = new ItemStack(Registries.ITEM.get(Identifier.of(result.get("id").getAsString())), count);
        context.drawItem(resultStack, resultX, resultY);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}