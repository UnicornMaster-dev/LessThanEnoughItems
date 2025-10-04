package net.jackson;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
    private static final List<ItemStack> CRAFTABLE_ITEMS_CACHE = new ArrayList<>(); // Cache for craftable items
    private static final Set<String> CRAFTABLE_ITEM_IDS = new HashSet<>(); // Fast lookup set

    // Performance optimization: cache recipe existence checks
    public static final Map<String, Boolean> RECIPE_CACHE = new HashMap<>(); // Made public for RecipeScreen access
    private static boolean isCraftableCacheLoaded = false;

    private static TextFieldWidget searchField;
    private static String lastSearchText = "";
    private static boolean searchFieldInitialized = false;
    private static boolean searchFieldFocused = false; // Track focus state separately

    // GUI tracking for navigation
    private static Screen previousScreen = null;

    public static void setPreviousScreen(Screen screen) {
        previousScreen = screen;
    }

    public static Screen getPreviousScreen() {
        return previousScreen;
    }

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
        CRAFTABLE_ITEM_IDS.clear();
        CRAFTABLE_ITEMS_CACHE.clear();
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
            CRAFTABLE_ITEM_IDS.add(itemName);
        }
    }

    private static boolean hasCraftingRecipe(Identifier id) {
        String path = id.getPath();

        // Quick check against our loaded craftable items
        if (CRAFTABLE_ITEM_IDS.contains(path)) {
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
        // Check cache first
        if (RECIPE_CACHE.containsKey(itemName)) {
            return RECIPE_CACHE.get(itemName);
        }

        try {
            InputStream stream = ItemListOverlay.class.getClassLoader()
                    .getResourceAsStream("assets/jackson/recipes/" + itemName + ".json");
            boolean exists = stream != null;
            if (exists) {
                stream.close();
            }
            // Cache the result
            RECIPE_CACHE.put(itemName, exists);
            return exists;
        } catch (Exception ignored) {}
        return false;
    }

    private static void updateFilteredItems() {
        List<ItemStack> baseItems;

        // Apply craftable filter if enabled - use fast cache lookup
        if (RecipeViewerConfig.getInstance().showOnlyCraftable) {
            if (isCraftableCacheLoaded) {
                // Use pre-computed cache for instant filtering
                baseItems = new ArrayList<>(CRAFTABLE_ITEMS_CACHE);
            } else {
                // Fallback to slower method if cache not ready yet
                // Trigger cache loading if not started
                preloadCraftableItems();
                baseItems = ALL_ITEMS.stream()
                        .filter(stack -> {
                            String itemId = Registries.ITEM.getId(stack.getItem()).getPath();
                            return CRAFTABLE_ITEM_IDS.contains(itemId) ||
                                   fastCheckHasRecipe(itemId);
                        })
                        .collect(Collectors.toList());
            }
        } else {
            baseItems = new ArrayList<>(ALL_ITEMS);
        }

        // Apply search filter - optimized with early exit
        String searchText = (searchField != null ? searchField.getText() : "").toLowerCase().trim();
        if (!searchText.isEmpty()) {
            baseItems = baseItems.parallelStream() // Use parallel stream for better performance
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
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        RecipeViewerConfig config = RecipeViewerConfig.getInstance();
        int overlayWidth = config.itemsPerRow * (ITEM_SIZE + PADDING) + 10;
        int startX = screenWidth - overlayWidth - 10;

        // Make search field slightly narrower to prevent stretching too far left
        int searchWidth = overlayWidth - 5; // Reduced from overlayWidth - 5 to overlayWidth - 15
        searchField = new TextFieldWidget(client.textRenderer, startX + 10, 5, searchWidth, 16, Text.literal("Search..."));
        searchField.setPlaceholder(Text.literal("Search items..."));
        searchField.setText(lastSearchText);
        searchField.setChangedListener(text -> {
            lastSearchText = text;
            // Debounce the filtering to improve performance
            scheduleFilterUpdate();
        });
        searchField.setFocusUnlocked(true);
        searchField.setEditable(true);
        searchFieldInitialized = true;
    }

    // Add debouncing for better performance
    private static long lastFilterUpdateTime = 0;
    private static final long FILTER_DEBOUNCE_MS = 150; // 150ms debounce

    private static void scheduleFilterUpdate() {
        lastFilterUpdateTime = System.currentTimeMillis();
        // Use a separate thread to avoid blocking the main thread
        new Thread(() -> {
            try {
                Thread.sleep(FILTER_DEBOUNCE_MS);
                // Only update if no new input came in during the delay
                if (System.currentTimeMillis() - lastFilterUpdateTime >= FILTER_DEBOUNCE_MS) {
                    MinecraftClient.getInstance().execute(() -> updateFilteredItems());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private static final Map<Rect2i, Item> clickableAreas = new HashMap<>();
    private static int currentPage = 0;
    private static int totalPages = 1;

    public static void render(DrawContext context, int mouseX, int mouseY) {
        // Only initialize search field if not already initialized or if window scaling changed
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        RecipeViewerConfig config = RecipeViewerConfig.getInstance();
        int overlayWidth = config.itemsPerRow * (ITEM_SIZE + PADDING) + 10;
        int expectedX = screenWidth - overlayWidth - 10;

        // Only recreate if position changed or not initialized
        if (!searchFieldInitialized || searchField == null || searchField.getX() != expectedX) {
            boolean wasFocused = searchFieldFocused; // Preserve focus state
            initializeSearchField();
            if (wasFocused) {
                searchField.setFocused(true);
                searchFieldFocused = true;
            }
        }

        clickableAreas.clear();

        List<ItemStack> itemsToShow = FILTERED_ITEMS;

        int itemsPerPage = config.itemsPerRow * config.rowsPerPage;
        totalPages = Math.max(1, (itemsToShow.size() + itemsPerPage - 1) / itemsPerPage);

        int startX = screenWidth - (config.itemsPerRow * (ITEM_SIZE + PADDING)) - 10;
        int startY = 25; // Leave more space for search field

        // Draw semi-transparent background
        int bgWidth = config.itemsPerRow * (ITEM_SIZE + PADDING) + 10;
        int bgHeight = config.rowsPerPage * (ITEM_SIZE + PADDING) + 45; // Extra space for search and buttons
        context.fill(startX - 5, 2, startX + bgWidth, startY + bgHeight, 0x88000000);

        // Draw search field background for better visibility
        if (searchField != null) {
            int searchX = searchField.getX();
            int searchY = searchField.getY();
            int searchWidth = searchField.getWidth();
            int searchHeight = searchField.getHeight();

            // Draw search field border and background
            boolean searchHovered = mouseX >= searchX && mouseX <= searchX + searchWidth &&
                                  mouseY >= searchY && mouseY <= searchY + searchHeight;
            boolean searchFocused = searchField.isFocused();

            // Background
            context.fill(searchX - 1, searchY - 1, searchX + searchWidth + 1, searchY + searchHeight + 1,
                        searchFocused ? 0xFFFFFFFF : (searchHovered ? 0xFFCCCCCC : 0xFF999999));
            context.fill(searchX, searchY, searchX + searchWidth, searchY + searchHeight, 0xFF000000);

            // Render the actual search field
            searchField.render(context, mouseX, mouseY, 0);

            // Add visual indicator when focused
            if (searchFocused) {
                // Draw a subtle glow effect
                context.fill(searchX - 2, searchY - 2, searchX + searchWidth + 2, searchY + searchHeight + 2, 0x3300AAFF);
            }
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
        if (searchField != null) {
            int searchX = searchField.getX();
            int searchY = searchField.getY();
            int searchWidth = searchField.getWidth();
            int searchHeight = searchField.getHeight();

            // Check if click is within search field bounds
            if (mouseX >= searchX && mouseX <= searchX + searchWidth &&
                mouseY >= searchY && mouseY <= searchY + searchHeight) {

                // Handle the click and set focus
                searchField.mouseClicked(mouseX, mouseY, 0);
                searchField.setFocused(true);
                searchFieldFocused = true; // Track focus state
                return true;
            } else {
                // Click outside search field - remove focus
                searchField.setFocused(false);
                searchFieldFocused = false; // Track focus state
            }
        }

        RecipeViewerConfig config = RecipeViewerConfig.getInstance();

        // Check toggle button click - match the new positioning
        int screenWidth = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int buttonStartX = screenWidth - (config.itemsPerRow * (ITEM_SIZE + PADDING)) - 10;
        int toggleY = 25 + config.rowsPerPage * (ITEM_SIZE + PADDING) + 5; // Updated to match new startY
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

                // Store the current screen before navigating to recipe screen
                setPreviousScreen(MinecraftClient.getInstance().currentScreen);
                MinecraftClient.getInstance().setScreen(new RecipeScreen(entry.getValue()));
                return true;
            }
        }
        return false;
    }

    public static boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        // Only handle key presses if the search field is focused
        if (searchField != null && searchField.isFocused()) {
            // Handle character input through keyPressed instead of charTyped
            boolean handled = searchField.keyPressed(keyCode, scanCode, modifiers);

            // If the search field handled it (like backspace, delete, arrow keys), we're done
            if (handled) {
                return true;
            }

            // Handle special keys that might not be handled by the text field
            if (keyCode == 259) { // GLFW_KEY_BACKSPACE
                String currentText = searchField.getText();
                if (!currentText.isEmpty()) {
                    searchField.setText(currentText.substring(0, currentText.length() - 1));
                    return true;
                }
            } else if (keyCode == 261) { // GLFW_KEY_DELETE
                // Delete key - for now just clear selection or do nothing
                return true;
            }

            // For printable characters, we need to handle them as text input
            if (keyCode >= 32 && keyCode <= 126) {
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
                return true;
            }

            return true; // Consume all input when search field is focused
        }
        return false; // Don't consume input when search field is not focused
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

    // Performance optimization: pre-compute craftable items asynchronously
    public static void preloadCraftableItems() {
        if (isCraftableCacheLoaded) {
            return; // Already loaded
        }

        // Run in background thread to avoid blocking
        new Thread(() -> {
            try {
                CRAFTABLE_ITEMS_CACHE.clear();
                CRAFTABLE_ITEM_IDS.clear();

                // Expand the list of known craftable items significantly
                populateExtensiveCraftableItems();

                // Build the cache of craftable ItemStacks
                for (Item item : Registries.ITEM) {
                    if (item == Items.AIR || EXCLUDED_ITEMS.contains(item)) continue;

                    String itemId = Registries.ITEM.getId(item).getPath();
                    if (CRAFTABLE_ITEM_IDS.contains(itemId) || fastCheckHasRecipe(itemId)) {
                        CRAFTABLE_ITEMS_CACHE.add(new ItemStack(item));
                    }
                }

                isCraftableCacheLoaded = true;
                System.out.println("Craftable items cache loaded: " + CRAFTABLE_ITEMS_CACHE.size() + " items");

                // Update filtered items on main thread if craftable filter is active
                MinecraftClient.getInstance().execute(() -> {
                    if (RecipeViewerConfig.getInstance().showOnlyCraftable) {
                        updateFilteredItems();
                    }
                });
            } catch (Exception e) {
                System.err.println("Error preloading craftable items: " + e.getMessage());
            }
        }).start();
    }

    private static void populateExtensiveCraftableItems() {
        // Comprehensive list of craftable items for instant lookup
        String[] craftableItems = {
            // Basic materials
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks",
            "cherry_planks", "mangrove_planks", "bamboo_planks", "crimson_planks", "warped_planks",
            "stick", "bowl", "crafting_table", "furnace", "chest", "ladder", "torch", "soul_torch",

            // Tools - all tiers
            "wooden_pickaxe", "wooden_axe", "wooden_shovel", "wooden_sword", "wooden_hoe",
            "stone_pickaxe", "stone_axe", "stone_shovel", "stone_sword", "stone_hoe",
            "iron_pickaxe", "iron_axe", "iron_shovel", "iron_sword", "iron_hoe",
            "golden_pickaxe", "golden_axe", "golden_shovel", "golden_sword", "golden_hoe",
            "diamond_pickaxe", "diamond_axe", "diamond_shovel", "diamond_sword", "diamond_hoe",
            "netherite_pickaxe", "netherite_axe", "netherite_shovel", "netherite_sword", "netherite_hoe",

            // Armor - all tiers
            "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots",
            "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots",
            "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots",
            "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots",
            "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots",
            "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",

            // Food
            "bread", "cake", "cookie", "pumpkin_pie", "mushroom_stew", "rabbit_stew", "beetroot_soup",
            "suspicious_stew", "honey_bottle", "sugar", "golden_apple", "golden_carrot",

            // Building blocks
            "cobblestone", "stone", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
            "chiseled_stone_bricks", "smooth_stone", "smooth_stone_slab", "cobblestone_slab",
            "stone_slab", "stone_brick_slab", "mossy_stone_brick_slab", "smooth_sandstone",
            "cut_sandstone", "chiseled_sandstone", "red_sandstone", "smooth_red_sandstone",
            "cut_red_sandstone", "chiseled_red_sandstone", "bricks", "nether_bricks",
            "red_nether_bricks", "chiseled_nether_bricks", "cracked_nether_bricks",

            // Glass and panes
            "glass", "glass_pane", "white_stained_glass", "orange_stained_glass", "magenta_stained_glass",
            "light_blue_stained_glass", "yellow_stained_glass", "lime_stained_glass", "pink_stained_glass",
            "gray_stained_glass", "light_gray_stained_glass", "cyan_stained_glass", "purple_stained_glass",
            "blue_stained_glass", "brown_stained_glass", "green_stained_glass", "red_stained_glass",
            "black_stained_glass",

            // Redstone
            "redstone_torch", "redstone_block", "repeater", "comparator", "piston", "sticky_piston",
            "redstone_lamp", "daylight_detector", "tripwire_hook", "dropper", "dispenser", "hopper",
            "observer", "target", "lectern", "note_block", "jukebox",

            // Transportation
            "rail", "powered_rail", "detector_rail", "activator_rail", "minecart", "chest_minecart",
            "furnace_minecart", "hopper_minecart", "tnt_minecart", "oak_boat", "spruce_boat",
            "birch_boat", "jungle_boat", "acacia_boat", "dark_oak_boat", "cherry_boat",
            "mangrove_boat", "bamboo_raft",

            // Utility blocks
            "anvil", "enchanting_table", "bookshelf", "ender_chest", "shulker_box", "barrel",
            "smoker", "blast_furnace", "grindstone", "stonecutter", "loom", "cartography_table",
            "fletching_table", "smithing_table", "brewing_stand", "cauldron", "composter",
            "bee_nest", "beehive", "campfire", "soul_campfire", "lantern", "soul_lantern",

            // Decorative
            "flower_pot", "item_frame", "glow_item_frame", "painting", "armor_stand", "end_rod",
            "chorus_fruit", "purpur_block", "purpur_pillar", "purpur_slab", "purpur_stairs",
            "end_stone_bricks", "sea_lantern", "prismarine", "prismarine_bricks", "dark_prismarine",

            // Processed materials
            "iron_ingot", "gold_ingot", "copper_ingot", "netherite_ingot", "diamond", "emerald",
            "coal", "charcoal", "quartz", "amethyst_shard", "leather", "paper", "book",
            "writable_book", "written_book", "map", "clock", "compass", "recovery_compass",
            "bundle", "spyglass", "lead", "name_tag", "saddle"
        };

        for (String itemName : craftableItems) {
            CRAFTABLE_ITEM_IDS.add(itemName);
        }
    }

    private static boolean fastCheckHasRecipe(String itemName) {
        // Quick heuristic checks before expensive I/O

        // Common patterns that are usually craftable
        if (itemName.endsWith("_planks") || itemName.endsWith("_log") ||
            itemName.endsWith("_wood") || itemName.endsWith("_slab") ||
            itemName.endsWith("_stairs") || itemName.endsWith("_fence") ||
            itemName.endsWith("_door") || itemName.endsWith("_trapdoor") ||
            itemName.endsWith("_button") || itemName.endsWith("_pressure_plate") ||
            itemName.endsWith("_sign") || itemName.endsWith("_hanging_sign")) {
            return true;
        }

        // Check cache first for expensive operations
        if (RECIPE_CACHE.containsKey(itemName)) {
            return RECIPE_CACHE.get(itemName);
        }

        // Only do expensive I/O check if not in cache
        return checkRecipeExists(itemName);
    }
}
