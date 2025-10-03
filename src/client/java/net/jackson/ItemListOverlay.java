package net.jackson;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ItemListOverlay {
    private static final int ITEM_SIZE = 16;
    private static final int PADDING = 4;

    private static final Set<Item> EXCLUDED_ITEMS = Set.of(
            Items.AIR
    );

    private static final List<ItemStack> ALL_ITEMS = new ArrayList<>();
    private static final List<ItemStack> FILTERED_ITEMS = new ArrayList<>();

    public static void reloadItems() {
        ALL_ITEMS.clear();
        FILTERED_ITEMS.clear();

        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            ItemStack stack = new ItemStack(item);

            // Filter: skip empty items
            if (stack.isEmpty()) continue;
            if (EXCLUDED_ITEMS.contains(item)) continue;

            ALL_ITEMS.add(stack);

            // Apply non-craftables filter if enabled
            if (!RecipeViewerConfig.getInstance().showOnlyCraftable || hasCraftingRecipe(id)) {
                FILTERED_ITEMS.add(stack);
            }
        }

        // Sort alphabetically
        ALL_ITEMS.sort(Comparator.comparing(stack -> stack.getName().getString()));
        FILTERED_ITEMS.sort(Comparator.comparing(stack -> stack.getName().getString()));
    }

    private static boolean hasCraftingRecipe(Identifier id) {
        String path = id.getPath();

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

        // Check if recipe file exists
        Path recipePath = FabricLoader.getInstance().getGameDir()
                .resolve("resources/assets/jackson/recipes/" + path + ".json");
        return Files.exists(recipePath);
    }

    private static final Map<Rect2i, Item> clickableAreas = new HashMap<>();
    private static int currentPage = 0;
    private static int totalPages = 1;

    public static void render(DrawContext context, int mouseX, int mouseY) {
        clickableAreas.clear();
        MinecraftClient client = MinecraftClient.getInstance();

        RecipeViewerConfig config = RecipeViewerConfig.getInstance();
        List<ItemStack> itemsToShow = config.showOnlyCraftable ? FILTERED_ITEMS : ALL_ITEMS;

        int itemsPerPage = config.itemsPerRow * config.rowsPerPage;
        totalPages = Math.max(1, (itemsToShow.size() + itemsPerPage - 1) / itemsPerPage);

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int startX = screenWidth - (config.itemsPerRow * (ITEM_SIZE + PADDING)) - 10;
        int startY = 20;

        // Draw semi-transparent background
        int bgWidth = config.itemsPerRow * (ITEM_SIZE + PADDING) + 10;
        int bgHeight = config.rowsPerPage * (ITEM_SIZE + PADDING) + 30;
        context.fill(startX - 5, startY - 5, startX + bgWidth, startY + bgHeight, 0x88000000);

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
        int toggleX = startX;
        int toggleY = startY + config.rowsPerPage * (ITEM_SIZE + PADDING) + 5;
        String toggleText = config.showOnlyCraftable ? "Show All" : "Craftables Only";
        int toggleWidth = client.textRenderer.getWidth(toggleText) + 8;

        boolean toggleHovered = mouseX >= toggleX && mouseX <= toggleX + toggleWidth &&
                               mouseY >= toggleY && mouseY <= toggleY + 12;

        context.fill(toggleX, toggleY, toggleX + toggleWidth, toggleY + 12,
                    toggleHovered ? 0xFFFFFFFF : 0xFF666666);
        context.drawText(client.textRenderer, toggleText, toggleX + 4, toggleY + 2,
                        toggleHovered ? 0x000000 : 0xFFFFFF, false);

        // Draw page number
        context.drawText(client.textRenderer, "Page " + (currentPage + 1) + " / " + totalPages,
                startX, toggleY + 15, 0xFFFFFF, false);

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
        RecipeViewerConfig config = RecipeViewerConfig.getInstance();

        // Check toggle button click
        int toggleX = MinecraftClient.getInstance().getWindow().getScaledWidth() -
                     (config.itemsPerRow * (ITEM_SIZE + PADDING)) - 10;
        int toggleY = 20 + config.rowsPerPage * (ITEM_SIZE + PADDING) + 5;
        String toggleText = config.showOnlyCraftable ? "Show All" : "Craftables Only";
        int toggleWidth = MinecraftClient.getInstance().textRenderer.getWidth(toggleText) + 8;

        if (mouseX >= toggleX && mouseX <= toggleX + toggleWidth &&
            mouseY >= toggleY && mouseY <= toggleY + 12) {
            config.toggleShowOnlyCraftable();
            reloadItems(); // Refresh the filtered list
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
