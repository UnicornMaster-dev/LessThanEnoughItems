package net.jackson;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class ItemListOverlay {
    private static final int ITEM_SIZE = 16;
    private static final int PADDING = 4;

    private static final Set<Item> EXCLUDED_ITEMS = Set.of(
            Items.AIR
    );

    private static final List<ItemStack> ALL_ITEMS = new ArrayList<>();
    private static final List<ItemStack> FILTERED_ITEMS = new ArrayList<>();
    private static final Set<String> CRAFTABLE_ITEMS = new HashSet<>();

    private static TextFieldWidget searchField;
    private static String lastSearchText = "";
    private static boolean searchFieldInitialized = false;

    public static void reloadItems() {
        ALL_ITEMS.clear();
        FILTERED_ITEMS.clear();
        loadCraftableItems();

        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            ItemStack stack = new ItemStack(item);

            // Filter: skip empty items
            if (stack.isEmpty()) continue;
            if (EXCLUDED_ITEMS.contains(item)) continue;

            ALL_ITEMS.add(stack);
        }

        // Sort alphabetically
        ALL_ITEMS.sort(Comparator.comparing(stack -> stack.getName().getString()));
        updateFilteredItems();
    }

    private static void loadCraftableItems() {
        CRAFTABLE_ITEMS.clear();
        try {
            // Load from resources instead of file system for better performance
            String[] recipeTypes = {"recipes", "smelting", "blasting"};
            for (String type : recipeTypes) {
                try (InputStream stream = ItemListOverlay.class.getClassLoader()
                        .getResourceAsStream("assets/jackson/" + type)) {
                    if (stream != null) {
                        // This is a simplified approach - in practice you'd scan the jar/resources
                        // For now, we'll use a heuristic approach based on common items
                        populateCommonCraftableItems();
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            // Fallback to heuristic approach
            populateCommonCraftableItems();
        }
    }

    private static void populateCommonCraftableItems() {
        // Add common craftable items to improve performance
        String[] commonCraftable = {
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks",
            "stick", "crafting_table", "furnace", "chest", "ladder", "torch", "stone_pickaxe", "stone_axe",
            "stone_shovel", "stone_sword", "stone_hoe", "iron_pickaxe", "iron_axe", "iron_shovel", "iron_sword",
            "iron_hoe", "diamond_pickaxe", "diamond_axe", "diamond_shovel", "diamond_sword", "diamond_hoe",
            "wooden_pickaxe", "wooden_axe", "wooden_shovel", "wooden_sword", "wooden_hoe", "bread", "cake",
            "cookie", "glass", "iron_ingot", "gold_ingot", "diamond", "emerald", "coal", "charcoal"
        };

        for (String itemName : commonCraftable) {
            CRAFTABLE_ITEMS.add(itemName);
        }
    }

    private static boolean hasCraftingRecipe(Identifier id) {
        String path = id.getPath();

        // Quick check against our loaded craftable items
        if (CRAFTABLE_ITEMS.contains(path)) {
            return true;
        }

        // Heuristic exclusion by item name for obviously non-craftable items
        if (path.contains("spawn_egg") ||
                path.contains("air") ||
                path.contains("debug") ||
                path.contains("structure_block") ||
                path.contains("jigsaw") ||
                path.contains("barrier") ||
                path.contains("command_block") ||
                path.contains("piston_head") ||
                path.contains("knowledge_book") ||
                path.contains("light")) {
            return false;
        }

        // Check if recipe file exists (cached approach)
        return checkRecipeExists(path);
    }

    private static boolean checkRecipeExists(String itemName) {
        try {
            InputStream stream = ItemListOverlay.class.getClassLoader()
                    .getResourceAsStream("assets/jackson/recipes/" + itemName + ".json");
            if (stream != null) {
                stream.close();
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void updateFilteredItems() {
        List<ItemStack> baseItems = new ArrayList<>(ALL_ITEMS);

        // Apply craftable filter if enabled
        if (RecipeViewerConfig.getInstance().showOnlyCraftable) {
            baseItems = baseItems.stream()
                    .filter(stack -> hasCraftingRecipe(Registries.ITEM.getId(stack.getItem())))
                    .collect(Collectors.toList());
        }

        // Apply search filter
        String searchText = (searchField != null ? searchField.getText() : "").toLowerCase().trim();
        if (!searchText.isEmpty()) {
            baseItems = baseItems.stream()
                    .filter(stack -> {
                        String itemName = stack.getName().getString().toLowerCase();
                        String itemId = Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase();
                        return itemName.contains(searchText) || itemId.contains(searchText);
                    })
                    .collect(Collectors.toList());
        }

        FILTERED_ITEMS.clear();
        FILTERED_ITEMS.addAll(baseItems);
    }

    private static void initializeSearchField() {
        if (!searchFieldInitialized || searchField == null) {
            MinecraftClient client = MinecraftClient.getInstance();
            int screenWidth = client.getWindow().getScaledWidth();
            RecipeViewerConfig config = RecipeViewerConfig.getInstance();
            int overlayWidth = config.itemsPerRow * (ITEM_SIZE + PADDING) + 10;
            int startX = screenWidth - overlayWidth - 10;

            searchField = new TextFieldWidget(client.textRenderer, startX, 5, overlayWidth - 5, 12, Text.literal("Search..."));
            searchField.setPlaceholder(Text.literal("Search items..."));
            searchField.setText(lastSearchText);
            searchField.setChangedListener(text -> {
                lastSearchText = text;
                updateFilteredItems();
            });
            searchFieldInitialized = true;
        }
    }

    private static final Map<Rect2i, Item> clickableAreas = new HashMap<>();
    private static int currentPage = 0;
    private static int totalPages = 1;

    public static void render(DrawContext context, int mouseX, int mouseY) {
        initializeSearchField();
        clickableAreas.clear();
        MinecraftClient client = MinecraftClient.getInstance();

        RecipeViewerConfig config = RecipeViewerConfig.getInstance();
        List<ItemStack> itemsToShow = FILTERED_ITEMS;

        int itemsPerPage = config.itemsPerRow * config.rowsPerPage;
        totalPages = Math.max(1, (itemsToShow.size() + itemsPerPage - 1) / itemsPerPage);

        int screenWidth = client.getWindow().getScaledWidth();
        int startX = screenWidth - (config.itemsPerRow * (ITEM_SIZE + PADDING)) - 10;
        int startY = 22; // Leave space for search field

        // Draw semi-transparent background
        int bgWidth = config.itemsPerRow * (ITEM_SIZE + PADDING) + 10;
        int bgHeight = config.rowsPerPage * (ITEM_SIZE + PADDING) + 45; // Extra space for search and buttons
        context.fill(startX - 5, 2, startX + bgWidth, startY + bgHeight, 0x88000000);

        // Render search field
        if (searchField != null) {
            searchField.render(context, mouseX, mouseY, 0);
        }

        int indexStart = currentPage * itemsPerPage;
        List<ItemStack> visibleItems = itemsToShow.stream()
                .skip(indexStart)
                .limit(itemsPerPage)
                .toList();

        Item hoveredItem = null;

        int row = 0, col = 0;
        for (ItemStack stack : visibleItems) {
            Item item = stack.getItem();
            int drawX = startX + col * (ITEM_SIZE + PADDING);
            int drawY = startY + row * (ITEM_SIZE + PADDING);

            Rect2i rect = new Rect2i(drawX, drawY, ITEM_SIZE, ITEM_SIZE);
            clickableAreas.put(rect, item);

            if (mouseX >= drawX && mouseX <= drawX + ITEM_SIZE &&
                    mouseY >= drawY && mouseY <= drawY + ITEM_SIZE) {
                context.fill(drawX - 1, drawY - 1, drawX + ITEM_SIZE + 1, drawY + ITEM_SIZE + 1, 0x88FFFFFF); // white hover
                hoveredItem = item;
            }

            // Use higher z-level for item rendering to ensure it's on top
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200); // Higher z-level
            context.drawItem(stack, drawX, drawY);
            context.getMatrices().pop();

            col++;
            if (col >= config.itemsPerRow) {
                col = 0;
                row++;
            }
        }

        // Draw toggle button for non-craftables filter
        int screenWidthForButton = client.getWindow().getScaledWidth();
        int buttonStartX = screenWidthForButton - (config.itemsPerRow * (ITEM_SIZE + PADDING)) - 10;
        int toggleY = startY + config.rowsPerPage * (ITEM_SIZE + PADDING) + 5;
        String toggleText = config.showOnlyCraftable ? "Show All" : "Craftables Only";
        int toggleWidth = client.textRenderer.getWidth(toggleText) + 8;

        boolean toggleHovered = mouseX >= buttonStartX && mouseX <= buttonStartX + toggleWidth &&
                               mouseY >= toggleY && mouseY <= toggleY + 12;

        context.fill(buttonStartX, toggleY, buttonStartX + toggleWidth, toggleY + 12,
                    toggleHovered ? 0xFFFFFFFF : 0xFF666666);
        context.drawText(client.textRenderer, toggleText, buttonStartX + 4, toggleY + 2,
                        toggleHovered ? 0x000000 : 0xFFFFFF, false);

        // Draw page number
        context.drawText(client.textRenderer, "Page " + (currentPage + 1) + " / " + totalPages,
                buttonStartX, toggleY + 15, 0xFFFFFF, false);

        // Tooltip - render at highest z-level
        if (hoveredItem != null) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 400); // Highest z-level for tooltips
            ItemStack stack = new ItemStack(hoveredItem);
            context.drawTooltip(client.textRenderer, List.of(stack.getName()), mouseX, mouseY);
            context.getMatrices().pop();
        }
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        // Handle search field clicks first
        if (searchField != null && searchField.mouseClicked(mouseX, mouseY, 0)) {
            return true;
        }

        RecipeViewerConfig config = RecipeViewerConfig.getInstance();

        // Check toggle button click
        int screenWidth = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int buttonStartX = screenWidth - (config.itemsPerRow * (ITEM_SIZE + PADDING)) - 10;
        int toggleY = 22 + config.rowsPerPage * (ITEM_SIZE + PADDING) + 5;
        String toggleText = config.showOnlyCraftable ? "Show All" : "Craftables Only";
        int toggleWidth = MinecraftClient.getInstance().textRenderer.getWidth(toggleText) + 8;

        if (mouseX >= buttonStartX && mouseX <= buttonStartX + toggleWidth &&
            mouseY >= toggleY && mouseY <= toggleY + 12) {
            config.toggleShowOnlyCraftable();
            updateFilteredItems(); // Use efficient update instead of full reload
            return true;
        }

        // Check item clicks
        for (Map.Entry<Rect2i, Item> entry : clickableAreas.entrySet()) {
            Rect2i rect = entry.getKey();
            if (mouseX >= rect.getX() && mouseX <= rect.getX() + rect.getWidth() &&
                    mouseY >= rect.getY() && mouseY <= rect.getY() + rect.getHeight()) {

                MinecraftClient.getInstance().setScreen(new RecipeScreen(entry.getValue()));
                return true;
            }
        }
        return false;
    }

    public static boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        if (searchField != null) {
            // Handle character input through keyPressed instead of charTyped
            boolean handled = searchField.keyPressed(keyCode, scanCode, modifiers);

            // For printable characters, we need to handle them as text input
            if (!handled && keyCode >= 32 && keyCode <= 126) {
                char character = (char) keyCode;
                // Handle shift modifier for uppercase
                if ((modifiers & 1) != 0) { // SHIFT modifier
                    if (character >= 'a' && character <= 'z') {
                        character = Character.toUpperCase(character);
                    }
                    // Handle shifted number row symbols
                    else if (character >= '0' && character <= '9') {
                        String shiftedSymbols = ")!@#$%^&*(";
                        character = shiftedSymbols.charAt(character - '0');
                    }
                }
                // Handle lowercase when shift is not pressed
                else if (character >= 'A' && character <= 'Z') {
                    character = Character.toLowerCase(character);
                }

                // Manually add character to search field
                String currentText = searchField.getText();
                String newText = currentText + character;
                searchField.setText(newText);
                handled = true;
            }

            return handled;
        }
        return false;
    }

    public static boolean handleScroll(double amount) {
        if (amount > 0 && currentPage > 0) {
            currentPage--;
            return true;
        } else if (amount < 0 && currentPage < totalPages - 1) {
            currentPage++;
            return true;
        }
        return false;
    }
}
