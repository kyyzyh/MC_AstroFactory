package com.xiaoyi.astrofactory.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import com.xiaoyi.astrofactory.ModRegistry;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机器配方总表 —— 熔炼炉 / 制作台 / 组装机共用的唯一数据源。
 *
 * <p>两类来源：</p>
 * <ol>
 *   <li><b>内置配方</b>：代码里写死的基础产线，保证零数据包也能玩；</li>
 *   <li><b>数据包配方</b>：{@code data/<namespace>/machine_recipes/**.json}，
 *       通过 {@link MachineRecipeLoader} 在每次资源重载时读取（{@code /reload} 生效）。</li>
 * </ol>
 *
 * <p>同一 id 出现两次时，数据包配方覆盖内置配方。</p>
 *
 * <p><b>重要：</b>本类只保存 {@link Item} 与数量，绝不在此构造 {@link ItemStack}。
 * 数据包重载（含启动首次加载）发生在物品数据组件绑定之前，
 * 那时 {@code new ItemStack(...)} 会抛 {@code NullPointerException: Components not bound yet}。</p>
 */
public final class RecipeRegistry {
    private RecipeRegistry() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 数据包配方目录：data/<ns>/machine_recipes/**.json */
    public static final FileToIdConverter CONVERTER = FileToIdConverter.json("machine_recipes");

    /** 内置配方（首次访问时构造，纯 Item 数据，不依赖组件绑定）。 */
    private static List<MachineRecipe> builtin = null;

    /** 数据包配方（每次重载整体替换）。 */
    private static Map<Identifier, MachineRecipe> fromData = Map.of();

    // ------------------------------------------------------------ 内置配方

    private static synchronized List<MachineRecipe> builtin() {
        if (builtin != null) {
            return builtin;
        }
        List<MachineRecipe> list = new ArrayList<>();

        // ---- 熔炼炉：原矿 -> 锭
        smelt(list, "raw_iron_to_iron_ingot", Items.RAW_IRON, Items.IRON_INGOT);
        smelt(list, "raw_copper_to_copper_ingot", Items.RAW_COPPER, Items.COPPER_INGOT);
        smelt(list, "raw_gold_to_gold_ingot", Items.RAW_GOLD, Items.GOLD_INGOT);
        smelt(list, "cobblestone_to_stone", Items.COBBLESTONE, Items.STONE);
        smelt(list, "sand_to_glass", Items.SAND, Items.GLASS);

        // ---- 制作台：锭 -> 零件
        craft(list, "iron_ingot_to_iron_gear", Items.IRON_INGOT, 2, ModRegistry.IRON_GEAR.get(), 1);
        craft(list, "copper_ingot_to_copper_coil", Items.COPPER_INGOT, 3, ModRegistry.COPPER_COIL.get(), 1);

        // ---- 组装机：零件 -> 成品（3x3 网格，行优先）
        //  g c g
        //  . c .
        //  . . .
        assemble(list, "circuit_board", new String[]{"gcg", ".c.", "..."},
                Map.of('g', ModRegistry.IRON_GEAR.get(),
                        'c', ModRegistry.COPPER_COIL.get()),
                ModRegistry.CIRCUIT_BOARD.get(), 1);

        builtin = List.copyOf(list);
        return builtin;
    }

    private static void smelt(List<MachineRecipe> out, String name, Item in, Item result) {
        out.add(new MachineRecipe(builtinId(name), MachineType.SMELTING,
                List.of(new MachineRecipe.Ingredient(in, 1)), result, 1,
                MachineType.SMELTING.defaultTicks()));
    }

    private static void craft(List<MachineRecipe> out, String name, Item in, int amount, Item result, int count) {
        out.add(new MachineRecipe(builtinId(name), MachineType.CRAFTING,
                List.of(new MachineRecipe.Ingredient(in, amount)), result, count,
                MachineType.CRAFTING.defaultTicks()));
    }

    private static void assemble(List<MachineRecipe> out, String name, String[] rows,
                                 Map<Character, Item> key, Item result, int count) {
        out.add(new MachineRecipe(builtinId(name), MachineType.ASSEMBLING,
                expandPattern(rows, key), result, count, MachineType.ASSEMBLING.defaultTicks()));
    }

    private static Identifier builtinId(String name) {
        return Identifier.fromNamespaceAndPath(com.xiaoyi.astrofactory.Astrofactory.MODID, "builtin/" + name);
    }

    /** 把 3 行图案 + 字符表展开成 9 格输入（null 表示该格不放东西）。 */
    private static List<MachineRecipe.Ingredient> expandPattern(String[] rows, Map<Character, Item> key) {
        List<MachineRecipe.Ingredient> list = new ArrayList<>(Arrays.asList(new MachineRecipe.Ingredient[9]));
        for (int r = 0; r < 3; r++) {
            String row = r < rows.length ? rows[r] : "...";
            for (int c = 0; c < 3; c++) {
                char ch = c < row.length() ? row.charAt(c) : '.';
                Item item = (ch == '.' || ch == ' ') ? null : key.get(ch);
                if (item != null) {
                    list.set(r * 3 + c, new MachineRecipe.Ingredient(item, 1));
                }
            }
        }
        return list;
    }

    // ------------------------------------------------------------ 查询

    /** 全部配方（内置 + 数据包，同 id 数据包优先）。 */
    public static List<MachineRecipe> all() {
        Map<Identifier, MachineRecipe> merged = new LinkedHashMap<>();
        for (MachineRecipe r : builtin()) {
            merged.put(r.id(), r);
        }
        merged.putAll(fromData);
        return List.copyOf(merged.values());
    }

    public static List<MachineRecipe> byType(MachineType type) {
        List<MachineRecipe> out = new ArrayList<>();
        for (MachineRecipe r : all()) {
            if (r.type() == type) {
                out.add(r);
            }
        }
        return out;
    }

    /** 单输入机器（熔炼 / 制作）查找匹配配方；无匹配返回 null。 */
    public static MachineRecipe findSingle(MachineType type, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        for (MachineRecipe r : all()) {
            if (r.type() == type && r.matchesSingle(input)) {
                return r;
            }
        }
        return null;
    }

    /** 组装机 3x3 网格查找匹配配方；无匹配返回 null。 */
    public static MachineRecipe findGrid(List<ItemStack> slots) {
        for (MachineRecipe r : all()) {
            if (r.type() == MachineType.ASSEMBLING && r.matchesGrid(slots)) {
                return r;
            }
        }
        return null;
    }

    /** 该物品能否作为某类单输入机器的原料。 */
    public static boolean isSingleInput(MachineType type, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (MachineRecipe r : all()) {
            if (r.type() == type && r.matchesSingle(stack)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ 数据包加载

    static void setDataRecipes(Map<Identifier, MachineRecipe> recipes) {
        fromData = Map.copyOf(recipes);
        LOGGER.info("[AstroFactory] 配方已重载：数据包 {} 条 + 内置 {} 条 = 合计 {} 条",
                fromData.size(), builtin().size(), all().size());
    }

    /** 扫描资源管理器，解析全部 machine_recipes/*.json；单条失败只记日志不影响其它。 */
    static Map<Identifier, MachineRecipe> parseAll(ResourceManager manager) {
        Map<Identifier, MachineRecipe> result = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Resource> entry : CONVERTER.listMatchingResources(manager).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier id = CONVERTER.fileToId(fileId);
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement root = StrictJsonParser.parse(reader);
                if (!root.isJsonObject()) {
                    LOGGER.error("[AstroFactory] 配方 {} 顶层必须是 JSON 对象", fileId);
                    continue;
                }
                MachineRecipe recipe = parseOne(id, root.getAsJsonObject());
                if (recipe != null) {
                    result.put(id, recipe);
                }
            } catch (Exception e) {
                LOGGER.error("[AstroFactory] 解析配方 {} 失败：{}", fileId, e.toString());
            }
        }
        return result;
    }

    private static MachineRecipe parseOne(Identifier id, JsonObject json) {
        String rawType = json.has("type") ? json.get("type").getAsString() : null;
        MachineType type = MachineType.byId(rawType);
        if (type == null) {
            LOGGER.error("[AstroFactory] 配方 {} 的 type 无法识别：{}", id, rawType);
            return null;
        }

        JsonElement res = json.get("result");
        if (res == null || res.isJsonNull()) {
            LOGGER.error("[AstroFactory] 配方 {} 缺少 result", id);
            return null;
        }
        int defaultCount = json.has("count") ? json.get("count").getAsInt() : 1;
        ItemCount result = readItemCount(res, defaultCount);
        if (result == null) {
            LOGGER.error("[AstroFactory] 配方 {} 的 result 无法解析", id);
            return null;
        }

        List<MachineRecipe.Ingredient> inputs;
        if (type.isGrid()) {
            inputs = readGrid(json);
        } else {
            inputs = readFlatInputs(json);
        }
        if (inputs.isEmpty()) {
            LOGGER.error("[AstroFactory] 配方 {} 缺少有效 ingredients / pattern", id);
            return null;
        }

        int ticks = json.has("time") ? json.get("time").getAsInt() : type.defaultTicks();
        return new MachineRecipe(id, type, inputs, result.item(), result.count(), Math.max(1, ticks));
    }

    /** 物品 + 数量的中间结果（避免构造 ItemStack）。 */
    private record ItemCount(Item item, int count) {}

    /** 单输入：ingredients 为物品 id 数组（取第一个）。 */
    private static List<MachineRecipe.Ingredient> readFlatInputs(JsonObject json) {
        JsonElement ing = json.get("ingredients");
        if (ing == null || !ing.isJsonArray()) {
            return List.of();
        }
        JsonArray arr = ing.getAsJsonArray();
        if (arr.isEmpty()) {
            return List.of();
        }
        ItemCount first = readItemCount(arr.get(0), 1);
        return first == null ? List.of() : List.of(new MachineRecipe.Ingredient(first.item(), first.count()));
    }

    /** 组装：优先 pattern+key，其次 9 格 ingredients 数组。 */
    private static List<MachineRecipe.Ingredient> readGrid(JsonObject json) {
        if (json.has("pattern") && json.get("pattern").isJsonArray()) {
            Map<Character, Item> key = new LinkedHashMap<>();
            if (json.has("key") && json.get("key").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("key").entrySet()) {
                    if (e.getKey().isEmpty()) {
                        continue;
                    }
                    Item item = readItem(e.getValue());
                    if (item != null) {
                        key.put(e.getKey().charAt(0), item);
                    }
                }
            }
            JsonArray rowsJson = json.getAsJsonArray("pattern");
            String[] rows = new String[rowsJson.size()];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = rowsJson.get(i).getAsString();
            }
            return expandPattern(rows, key);
        }

        if (json.has("ingredients") && json.get("ingredients").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("ingredients");
            List<MachineRecipe.Ingredient> list = new ArrayList<>(Arrays.asList(new MachineRecipe.Ingredient[9]));
            for (int i = 0; i < Math.min(9, arr.size()); i++) {
                ItemCount got = readItemCount(arr.get(i), 1);
                if (got != null) {
                    list.set(i, new MachineRecipe.Ingredient(got.item(), got.count()));
                }
            }
            return list;
        }
        return List.of();
    }

    /** 支持 "minecraft:iron_ingot" 字符串或 {"item": ..., "count": n} 对象。 */
    private static ItemCount readItemCount(JsonElement element, int defaultCount) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            Item item = readItem(element);
            return item == null ? null : new ItemCount(item, Math.max(1, defaultCount));
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Item item = obj.has("item") ? readItem(obj.get("item")) : null;
            if (item == null) {
                return null;
            }
            int count = obj.has("count") ? obj.get("count").getAsInt() : Math.max(1, defaultCount);
            return new ItemCount(item, Math.max(1, count));
        }
        return null;
    }

    private static Item readItem(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String raw = element.getAsString();
        Identifier id;
        try {
            id = Identifier.parse(raw);
        } catch (Exception e) {
            LOGGER.error("[AstroFactory] 物品 id 非法：{}", raw);
            return null;
        }
        var holder = BuiltInRegistries.ITEM.get(id);
        if (holder.isEmpty()) {
            LOGGER.error("[AstroFactory] 找不到物品：{}", raw);
            return null;
        }
        return holder.get().value();
    }
}
