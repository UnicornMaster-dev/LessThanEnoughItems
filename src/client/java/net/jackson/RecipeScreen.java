package net.jackson;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class RecipeScreen extends Screen {
    private final Item targetItem;
    private JsonObject recipe;
    private TextFieldWidget searchField;
    private ButtonWidget craftableToggleButton;
    private List<Item> allItems;
    private List<Item> filteredItems;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private int ingredientCycleIndex = 0;
    private long lastCycleTime = 0;
    private static final long CYCLE_INTERVAL_MS = 1000; // 1 second per ingredient

    // UI Constants
    private static final int SLOT_SIZE = 18;
    private static final int ITEM_LIST_WIDTH = 160;
    private static final int RECIPE_AREA_WIDTH = 200;
    private static final int MARGIN = 10;

    public RecipeScreen(Item item) {
        super(Text.literal("Recipe Viewer"));
        this.targetItem = item;
        loadRecipe();
        initializeItemList();
    }

    private void initializeItemList() {
        allItems = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item != Items.AIR) {
                allItems.add(item);
            }
        }
        updateFilteredItems();
    }

    private void updateFilteredItems() {
        List<Item> baseList = allItems;

        // Apply craftable filter if enabled
        if (RecipeViewerConfig.getInstance().showOnlyCraftable) {
            baseList = allItems.stream()
                .filter(this::hasRecipe)
                .collect(Collectors.toList());
        }

        // Apply search filter
        String searchText = searchField != null ? searchField.getText().toLowerCase() : "";
        if (searchText.isEmpty()) {
            filteredItems = new ArrayList<>(baseList);
        } else {
            filteredItems = baseList.stream()
                .filter(item -> {
                    String itemName = Registries.ITEM.getId(item).getPath().toLowerCase();
                    return itemName.contains(searchText);
                })
                .collect(Collectors.toList());
        }

        scrollOffset = 0;
        updateMaxScroll();
    }

    private boolean hasRecipe(Item item) {
        String itemName = Registries.ITEM.getId(item).getPath();
        Identifier file = Identifier.of("jackson", "recipes/" + itemName + ".json");

        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(
                        getClass().getClassLoader().getResourceAsStream("assets/" + file.getNamespace() + "/" + file.getPath()))
        )) {
            return true; // If we can load it, it has a recipe
        } catch (Exception e) {
            return false; // No recipe found
        }
    }

    @Override
    protected void init() {
        super.init();

        // Search field
        searchField = new TextFieldWidget(this.textRenderer, MARGIN, MARGIN, ITEM_LIST_WIDTH - 20, 20, Text.literal("Search..."));
        searchField.setPlaceholder(Text.literal("Search items..."));
        searchField.setChangedListener(this::onSearchChanged);
        this.addSelectableChild(searchField);

        // Craftable filter toggle button
        craftableToggleButton = ButtonWidget.builder(
            Text.literal(RecipeViewerConfig.getInstance().showOnlyCraftable ? "All Items" : "Craftable Only"),
            button -> {
                RecipeViewerConfig.getInstance().toggleShowOnlyCraftable();
                button.setMessage(Text.literal(RecipeViewerConfig.getInstance().showOnlyCraftable ? "All Items" : "Craftable Only"));
                updateFilteredItems();
            }
        ).dimensions(MARGIN, 35, ITEM_LIST_WIDTH - 20, 20).build();
        this.addDrawableChild(craftableToggleButton);

        updateMaxScroll();
    }

    private void onSearchChanged(String search) {
        updateFilteredItems();
    }

    private void updateMaxScroll() {
        int itemsPerRow = ITEM_LIST_WIDTH / SLOT_SIZE;
        int visibleRows = (height - 105) / SLOT_SIZE; // Adjusted for filter button
        int totalRows = (filteredItems.size() + itemsPerRow - 1) / itemsPerRow;
        maxScroll = Math.max(0, totalRows - visibleRows);
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
            recipe = null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Draw item list background
        context.fill(0, 0, ITEM_LIST_WIDTH, height, 0x88000000);

        // Draw recipe area background
        context.fill(ITEM_LIST_WIDTH, 0, width, height, 0x44000000);

        super.render(context, mouseX, mouseY, delta);

        // Render search field
        searchField.render(context, mouseX, mouseY, delta);

        // Render item list
        renderItemList(context, mouseX, mouseY);

        // Render recipe area
        renderRecipeArea(context, mouseX, mouseY);
    }

    private void renderItemList(DrawContext context, int mouseX, int mouseY) {
        int startY = 65; // Adjusted to account for filter button
        int itemsPerRow = ITEM_LIST_WIDTH / SLOT_SIZE;
        int x = MARGIN;
        int y = startY;
        int row = 0;
        int col = 0;

        for (int i = scrollOffset * itemsPerRow; i < filteredItems.size() && row < (height - startY) / SLOT_SIZE; i++) {
            Item item = filteredItems.get(i);

            // Draw slot background
            drawSlot(context, x + col * SLOT_SIZE, y + row * SLOT_SIZE, item == targetItem);

            // Draw item
            context.drawItem(new ItemStack(item), x + col * SLOT_SIZE + 1, y + row * SLOT_SIZE + 1);

            // Check for hover and click
            if (mouseX >= x + col * SLOT_SIZE && mouseX < x + (col + 1) * SLOT_SIZE &&
                mouseY >= y + row * SLOT_SIZE && mouseY < y + (row + 1) * SLOT_SIZE) {
                // Draw hover effect
                context.fill(x + col * SLOT_SIZE, y + row * SLOT_SIZE,
                           x + (col + 1) * SLOT_SIZE, y + (row + 1) * SLOT_SIZE, 0x80FFFFFF);
            }

            col++;
            if (col >= itemsPerRow) {
                col = 0;
                row++;
            }
        }
    }

    private void renderRecipeArea(DrawContext context, int mouseX, int mouseY) {
        int recipeX = ITEM_LIST_WIDTH + MARGIN;
        int recipeY = 40;

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Recipe: " + Registries.ITEM.getId(targetItem).getPath()),
            ITEM_LIST_WIDTH + RECIPE_AREA_WIDTH / 2, 20, 0xFFFFFF);

        if (recipe == null) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("No recipe found"),
                ITEM_LIST_WIDTH + RECIPE_AREA_WIDTH / 2, recipeY + 50, 0xFF0000);
            return;
        }

        String type = recipe.get("type").getAsString();

        switch (type) {
            case "minecraft:crafting_shaped":
                renderShapedCrafting(context, recipe, recipeX, recipeY);
                break;
            case "minecraft:crafting_shapeless":
                renderShapelessCrafting(context, recipe, recipeX, recipeY);
                break;
            case "minecraft:blasting":
            case "minecraft:smelting":
                renderCooking(context, recipe, type, recipeX, recipeY);
                break;
            case "minecraft:smithing_transform":
                renderSmithing(context, recipe, recipeX, recipeY);
                break;
            default:
                context.drawCenteredTextWithShadow(this.textRenderer,
                    "Unknown recipe type: " + type,
                    ITEM_LIST_WIDTH + RECIPE_AREA_WIDTH / 2, recipeY + 50, 0xFF0000);
                break;
        }
    }

    private void renderShapedCrafting(DrawContext context, JsonObject json, int startX, int startY) {
        JsonArray pattern = json.getAsJsonArray("pattern");
        JsonObject key = json.getAsJsonObject("key");
        JsonObject result = json.getAsJsonObject("result");

        Map<Character, ItemStack> keyMap = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            char symbol = entry.getKey().charAt(0);
            String id = entry.getValue().getAsString();
            Item item = resolveItemFromIdOrTag(id);
            keyMap.put(symbol, new ItemStack(item));
        }

        // Draw crafting grid background
        int gridSize = 3;
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                drawSlot(context, startX + col * 20, startY + row * 20, false);
            }
        }

        // Draw pattern items
        for (int row = 0; row < pattern.size() && row < gridSize; row++) {
            String line = pattern.get(row).getAsString();
            for (int col = 0; col < line.length() && col < gridSize; col++) {
                char symbol = line.charAt(col);
                if (symbol != ' ') {
                    ItemStack stack = keyMap.getOrDefault(symbol, ItemStack.EMPTY);
                    if (!stack.isEmpty()) {
                        context.drawItem(stack, startX + col * 20 + 1, startY + row * 20 + 1);
                    }
                }
            }
        }

        // Draw arrow with correct texture coordinates
        context.drawTexture(RenderLayer::getGuiTextured,
                          Identifier.of("minecraft", "textures/gui/container/crafting_table.png"),
                          startX + 70, startY + 20, 89.0f, 15.0f, 22, 15, 256, 256);

        // Draw result slot and item
        drawSlot(context, startX + 100, startY + 20, false);
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        ItemStack resultStack = new ItemStack(resolveItemFromIdOrTag(result.get("id").getAsString()), count);
        context.drawItem(resultStack, startX + 101, startY + 21);

        // Draw count text manually if more than 1
        if (count > 1) {
            String countText = String.valueOf(count);
            int textX = startX + 101 + 16 - this.textRenderer.getWidth(countText);
            int textY = startY + 21 + 6; // Fixed positioning - use startY and reduce offset
            // Draw with shadow and higher z-level to ensure it's in front
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            context.drawText(this.textRenderer, countText, textX, textY, 0xFFFFFF, true);
            context.getMatrices().pop();
        }
    }

    private void renderShapelessCrafting(DrawContext context, JsonObject json, int startX, int startY) {
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

        // Draw crafting grid background
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlot(context, startX + col * 20, startY + row * 20, false);
            }
        }

        // Draw ingredients
        for (int i = 0; i < ingredientIds.size() && i < 9; i++) {
            int row = i / 3;
            int col = i % 3;
            String id = ingredientIds.get(i);
            Item item = resolveItemFromIdOrTag(id);
            context.drawItem(new ItemStack(item), startX + col * 20 + 1, startY + row * 20 + 1);
        }

        // Draw arrow
        context.drawTexture(RenderLayer::getGuiTextured,
                          Identifier.of("minecraft", "textures/gui/container/crafting_table.png"),
                          startX + 70, startY + 20, 89.0f, 15.0f, 22, 15, 256, 256);

        // Draw result
        JsonObject result = json.getAsJsonObject("result");
        drawSlot(context, startX + 100, startY + 20, false);
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        ItemStack resultStack = new ItemStack(resolveItemFromIdOrTag(result.get("id").getAsString()), count);
        context.drawItem(resultStack, startX + 101, startY + 21);

        // Draw count text manually if more than 1 (render in front)
        if (count > 1) {
            String countText = String.valueOf(count);
            int textX = startX + 101 + 16 - this.textRenderer.getWidth(countText);
            int textY = startY + 21 + 16 - this.textRenderer.fontHeight;
            // Draw with shadow and higher z-level to ensure it's in front
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            context.drawText(this.textRenderer, countText, textX, textY, 0xFFFFFF, true);
            context.getMatrices().pop();
        }
    }

    private void renderCooking(DrawContext context, JsonObject json, String type, int startX, int startY) {
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

        // Draw furnace interface
        drawSlot(context, startX, startY, false); // Input slot
        drawSlot(context, startX, startY + 40, false); // Fuel slot (placeholder)
        drawSlot(context, startX + 80, startY + 20, false); // Result slot

        // Draw ingredient
        if (!ingredientIds.isEmpty()) {
            String id = ingredientIds.get(ingredientCycleIndex);
            Item item = resolveItemFromIdOrTag(id);
            context.drawItem(new ItemStack(item), startX + 1, startY + 1);
        }

        // Draw fuel (coal as example)
        context.drawItem(new ItemStack(Items.COAL), startX + 1, startY + 41);

        // Draw furnace progress arrow
        context.drawTexture(RenderLayer::getGuiTextured,
                          Identifier.of("minecraft", "textures/gui/container/furnace.png"),
                          startX + 30, startY + 20, 79.0f, 34.0f, 24, 17, 256, 256);

        // Draw cooking time
        int cookingTime = json.has("cookingtime") ? json.get("cookingtime").getAsInt() : 200;
        float experience = json.has("experience") ? json.get("experience").getAsFloat() : 0.0f;

        context.drawTextWithShadow(this.textRenderer,
            Text.literal("Time: " + (cookingTime / 20.0f) + "s"),
            startX, startY + 70, 0xAAAAA);
        context.drawTextWithShadow(this.textRenderer,
            Text.literal("XP: " + experience),
            startX, startY + 85, 0xAAAAA);

        // Draw result
        JsonObject result = json.getAsJsonObject("result");
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        ItemStack resultStack = new ItemStack(resolveItemFromIdOrTag(result.get("id").getAsString()), count);
        context.drawItem(resultStack, startX + 81, startY + 21);

        // Draw count text manually if more than 1
        if (count > 1) {
            String countText = String.valueOf(count);
            int textX = startX + 81 + 16 - this.textRenderer.getWidth(countText);
            int textY = startY + 21 + 16 - this.textRenderer.fontHeight;
            // Draw with shadow and higher z-level to ensure it's in front
            context.drawText(this.textRenderer, countText, textX, textY, 0xFFFFFF, true);
        }
    }

    private void renderSmithing(DrawContext context, JsonObject json, int startX, int startY) {
        // Draw smithing table slots
        drawSlot(context, startX, startY, false); // Template
        drawSlot(context, startX + 25, startY, false); // Base
        drawSlot(context, startX + 50, startY, false); // Addition
        drawSlot(context, startX + 100, startY, false); // Result

        // Draw template
        if (json.has("template")) {
            Item template = resolveItemFromIdOrTag(json.get("template").getAsString());
            context.drawItem(new ItemStack(template), startX + 1, startY + 1);
        }

        // Draw base item
        if (json.has("base")) {
            Item base = resolveItemFromIdOrTag(json.get("base").getAsString());
            context.drawItem(new ItemStack(base), startX + 26, startY + 1);
        }

        // Draw addition item
        if (json.has("addition")) {
            Item addition = resolveItemFromIdOrTag(json.get("addition").getAsString());
            context.drawItem(new ItemStack(addition), startX + 51, startY + 1);
        }

        // Draw arrow
        context.drawTexture(RenderLayer::getGuiTextured,
                          Identifier.of("minecraft", "textures/gui/container/smithing.png"),
                          startX + 75, startY, 44.0f, 15.0f, 20, 15, 256, 256);

        // Draw result
        JsonObject result = json.getAsJsonObject("result");
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        ItemStack resultStack = new ItemStack(resolveItemFromIdOrTag(result.get("id").getAsString()), count);
        context.drawItem(resultStack, startX + 101, startY + 1);

        // Draw count text manually if more than 1
        if (count > 1) {
            String countText = String.valueOf(count);
            int textX = startX + 101 + 16 - this.textRenderer.getWidth(countText);
            int textY = startY + 1 + 16 - this.textRenderer.fontHeight;
            context.drawText(this.textRenderer, countText, textX, textY, 0xFFFFFF, true);
        }

        // Draw labels
        context.drawTextWithShadow(this.textRenderer, Text.literal("Template"), startX, startY + 25, 0xAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Base"), startX + 25, startY + 25, 0xAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Addition"), startX + 50, startY + 25, 0xAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Result"), startX + 100, startY + 25, 0xAAAAA);
    }

    private void drawSlot(DrawContext context, int x, int y, boolean selected) {
        int color = selected ? 0xFFFFFFFF : 0xFF8B8B8B;
        // Draw slot border
        context.fill(x - 1, y - 1, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, color);
        context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF373737);
    }

    // Helper method to resolve tags to actual items
    private Item resolveItemFromIdOrTag(String idOrTag) {
        if (idOrTag.startsWith("#")) {
            // This is a tag, map it to a representative item
            return getRepresentativeItemFromTag(idOrTag);
        } else {
            // Regular item ID - remove any leading hashtag if present
            String cleanId = idOrTag.startsWith("#") ? idOrTag.substring(1) : idOrTag;
            try {
                return Registries.ITEM.get(Identifier.of(cleanId));
            } catch (Exception e) {
                System.err.println("Failed to parse item ID: " + cleanId);
                return Items.BARRIER; // Fallback item
            }
        }
    }

    private Item getRepresentativeItemFromTag(String tag) {
        // Map common tags to representative items
        switch (tag) {
            case "#minecraft:planks":
                return Items.OAK_PLANKS;
            case "#minecraft:logs":
            case "#minecraft:logs_that_burn":
                return Items.OAK_LOG;
            case "#minecraft:acacia_logs":
                return Items.ACACIA_LOG;
            case "#minecraft:birch_logs":
                return Items.BIRCH_LOG;
            case "#minecraft:cherry_logs":
                return Items.CHERRY_LOG;
            case "#minecraft:dark_oak_logs":
                return Items.DARK_OAK_LOG;
            case "#minecraft:jungle_logs":
                return Items.JUNGLE_LOG;
            case "#minecraft:mangrove_logs":
                return Items.MANGROVE_LOG;
            case "#minecraft:oak_logs":
                return Items.OAK_LOG;
            case "#minecraft:spruce_logs":
                return Items.SPRUCE_LOG;
            case "#minecraft:stone_crafting_materials":
                return Items.COBBLESTONE;
            case "#minecraft:coals":
                return Items.COAL;
            case "#minecraft:wooden_slabs":
                return Items.OAK_SLAB;
            case "#minecraft:bundles":
                return Items.BUNDLE;
            case "#minecraft:shulker_boxes":
                return Items.SHULKER_BOX;
            case "#minecraft:trim_materials":
                return Items.IRON_INGOT;
            case "#minecraft:trimmable_armor":
                return Items.IRON_CHESTPLATE;
            case "#minecraft:netherite_tool_materials":
                return Items.NETHERITE_INGOT;
            case "#minecraft:crimson_stems":
                return Items.CRIMSON_STEM;
            default:
                // If unknown tag, try to extract the item name and use that
                String cleanTag = tag.replace("#minecraft:", "");
                if (cleanTag.endsWith("_logs")) {
                    // Try to find a log item matching the pattern
                    String logType = cleanTag.replace("_logs", "_log");
                    try {
                        return Registries.ITEM.get(Identifier.of("minecraft", logType));
                    } catch (Exception e) {
                        System.err.println("Unknown tag: " + tag + ", could not resolve to " + logType + ", using oak log as fallback");
                        return Items.OAK_LOG;
                    }
                }
                System.err.println("Unknown tag: " + tag + ", using barrier as fallback");
                return Items.BARRIER;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Handle item list clicks - use same startY as rendering
        if (mouseX < ITEM_LIST_WIDTH && mouseY > 65) {
            int startY = 65; // Match the rendering startY
            int itemsPerRow = ITEM_LIST_WIDTH / SLOT_SIZE;
            int col = (int) ((mouseX - MARGIN) / SLOT_SIZE);
            int row = (int) ((mouseY - startY) / SLOT_SIZE) + scrollOffset;

            if (col >= 0 && col < itemsPerRow) {
                int index = row * itemsPerRow + col;
                if (index >= 0 && index < filteredItems.size()) {
                    Item clickedItem = filteredItems.get(index);
                    if (client != null) {
                        client.setScreen(new RecipeScreen(clickedItem));
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < ITEM_LIST_WIDTH) {
            scrollOffset = MathHelper.clamp(scrollOffset - (int) verticalAmount, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        String searchText = searchField.getText();
        super.resize(client, width, height);
        this.init();
        searchField.setText(searchText);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
