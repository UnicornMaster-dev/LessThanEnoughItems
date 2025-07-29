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
    public static int ENTRIES_PER_ROW = 15;
    public static int ROWS_PER_PAGE = 20;

    private static final Set<Item> EXCLUDED_ITEMS = Set.of(
            Items.AIR
    );

    private static final List<ItemStack> ALL_ITEMS = new ArrayList<>();

    public static void reloadItems() {
        ALL_ITEMS.clear();

        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            ItemStack stack = new ItemStack(item);

            // Filter: skip empty or uncraftable
            if (stack.isEmpty()) continue;
            if (isExcluded(id)) continue;

            ALL_ITEMS.add(stack);
        }

        // Sort alphabetically (optional)
        ALL_ITEMS.sort(Comparator.comparing(stack -> stack.getName().getString()));
    }

    private static boolean isExcluded(Identifier id) {
        String path = id.getPath();

        // Heuristic exclusion by item name
        if (path.contains("spawn_egg") ||
                path.contains("sapling") ||
                path.contains("air") ||
                path.contains("debug") ||
                path.contains("structure_block") ||
                path.contains("jigsaw") ||
                path.contains("barrier") ||
                path.contains("command_block") ||
                path.contains("piston_head") ||
                path.contains("knowledge_book") ||
                path.contains("light")) {
            return true;
        }

        // Exclude items without a recipe JSON in our mod
        Path recipePath = FabricLoader.getInstance().getGameDir()
                .resolve("resources/assets/jackson/recipes/" + path + ".json");
        if (!Files.exists(recipePath)) {
            return true;
        }

        return false;
    }


    private static final Map<Rect2i, Item> clickableAreas = new HashMap<>();
    private static int currentPage = 0;
    private static int totalPages = 1;

    private static List<Item> getFilteredItems() {
        return Registries.ITEM.stream()
                .filter(item -> !EXCLUDED_ITEMS.contains(Registries.ITEM.getId(item).toString()))
                .sorted(Comparator.comparing(item -> Registries.ITEM.getId(item).toString()))
                .toList();
    }

    public static void render(DrawContext context, int mouseX, int mouseY) {
        clickableAreas.clear();
        MinecraftClient client = MinecraftClient.getInstance();

        List<Item> allItems = getFilteredItems();
        int itemsPerPage = ENTRIES_PER_ROW * ROWS_PER_PAGE;
        totalPages = (allItems.size() + itemsPerPage - 1) / itemsPerPage;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int startX = screenWidth - (ENTRIES_PER_ROW * (ITEM_SIZE + PADDING)) - 10;
        int startY = 20;

        int indexStart = currentPage * itemsPerPage;
        List<Item> visibleItems = allItems.stream()
                .skip(indexStart)
                .limit(itemsPerPage)
                .toList();

        Item hoveredItem = null;

        int row = 0, col = 0;
        for (Item item : visibleItems) {
            int drawX = startX + col * (ITEM_SIZE + PADDING);
            int drawY = startY + row * (ITEM_SIZE + PADDING);

            Rect2i rect = new Rect2i(drawX, drawY, ITEM_SIZE, ITEM_SIZE);
            clickableAreas.put(rect, item);

            if (mouseX >= drawX && mouseX <= drawX + ITEM_SIZE &&
                    mouseY >= drawY && mouseY <= drawY + ITEM_SIZE) {
                context.fill(drawX - 1, drawY - 1, drawX + ITEM_SIZE + 1, drawY + ITEM_SIZE + 1, 0x88000000); // grey hover
                hoveredItem = item;
            }

            context.drawItem(new ItemStack(item), drawX, drawY);
            col++;
            if (col >= ENTRIES_PER_ROW) {
                col = 0;
                row++;
            }
        }

        // Draw page number
        context.drawText(client.textRenderer, "Page " + (currentPage + 1) + " / " + totalPages,
                startX, startY + ROWS_PER_PAGE * (ITEM_SIZE + PADDING) + 5, 0xFFFFFF, false);

        // Tooltip
        if (hoveredItem != null) {
            ItemStack stack = new ItemStack(hoveredItem);
            context.drawTooltip(client.textRenderer, List.of(stack.getName()), mouseX, mouseY);
        }
    }

    public static boolean handleClick(double mouseX, double mouseY) {
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
